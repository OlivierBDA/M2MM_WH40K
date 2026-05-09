package me.data_architect.m2mm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class CloudLlmService(private val apiKey: String) : LlmService {

    // Utilisation du modèle Gemma 4 26B A4B IT officiel
    private val modelName = "gemma-4-26b-a4b-it"
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

    suspend fun generateEncouragementStatic(): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("La clé API n'est pas configurée dans les paramètres."))
        }

        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val prompt = "Tu es le coach d'une application de motivation. Fais un très court message d'encouragement (1 phrase) pour un utilisateur."
            
            // Build the JSON payload for Gemini API
            val payload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                // Parse the response: candidates[0].content.parts[0].text
                val text = jsonResponse
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                Result.success(text.trim())
            } else {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "Erreur HTTP $responseCode"
                Result.failure(Exception("Erreur API : $errorMessage"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun generateEncouragementDynamic(context: GameContext): Result<LlmResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("La clé API n'est pas configurée dans les paramètres."))
        }

        try {
            android.util.Log.d("LlmRepository", "Starting API call...")
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000 // 15 seconds
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val prompts = buildLlmPrompts(context)
            val systemInstructionText = prompts.first
            val userPrompt = prompts.second
            
            // Build the JSON payload for Gemini API with systemInstruction
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userPrompt)
                            })
                        })
                    })
                })
                // Force JSON output with strict Schema
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("reasoning", JSONObject().apply {
                                put("type", "STRING")
                            })
                            put("answer", JSONObject().apply {
                                put("type", "STRING")
                            })
                        })
                        put("required", org.json.JSONArray().apply {
                            put("reasoning")
                            put("answer")
                        })
                    })
                })
            }

            android.util.Log.d("LlmRepository", "Sending combined prompt:\n$systemInstructionText\n\n$userPrompt")

            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                val text = jsonResponse
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                android.util.Log.d("LlmRepository", "Raw LLM output:\n$text")
                
                // Remove potential markdown code blocks around JSON
                val cleanText = text.replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                                    .replace(Regex("```\\s*"), "")
                                    .trim()
                
                android.util.Log.d("LlmRepository", "Clean LLM output:\n$cleanText")
                
                // Parse the internal JSON generated by the LLM
                val parsedJson = JSONObject(cleanText)
                val finalAnswer = parsedJson.optString("answer", "Je ne trouve pas les mots pour exprimer ma déception.")
                val reasoning = parsedJson.optString("reasoning", "")
                
                // Construct a display format for the UI test
                val formattedResponse = if (reasoning.isNotEmpty()) {
                    "🤔 Réflexion du LLM :\n$reasoning\n\n🎯 Réponse finale :\n$finalAnswer"
                } else {
                    finalAnswer
                }
                
                val combinedPrompt = "$systemInstructionText\n\n$userPrompt"
                Result.success(LlmResult(combinedPrompt, formattedResponse))
            } else {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "Erreur HTTP $responseCode"
                android.util.Log.e("LlmRepository", "API Error HTTP $responseCode: $errorMessage")
                Result.failure(Exception("Erreur API : $errorMessage"))
            }
        } catch (e: Exception) {
            android.util.Log.e("LlmRepository", "Exception during API call: ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

data class LlmResult(
    val prompt: String,
    val response: String
)

data class GameContext(
    val primarchName: String,
    val legionName: String,
    val currentScore: Int,
    val currentLevel: String,
    val pointsToNextLevel: Int,
    val score7DaysAgo: Int,
    val activitiesDetails: List<String>,
    val coachHistory: List<String>
)

fun buildLlmPrompts(context: GameContext): Pair<String, String> {
    val systemInstructionText = """
        Tu es le Primarque ${context.primarchName} de la légion ${context.legionName}.
        Tu es un coach de motivation personnel impitoyable mais juste, inspirant et charismatique.
        Ton objectif est d'encourager le joueur.
        
        RÈGLES ABSOLUES :
        1. TU DOIS RÉPONDRE EXCLUSIVEMENT AU FORMAT JSON.
        2. LE JSON DOIT CONTENIR EXACTEMENT DEUX CLÉS : 'reasoning' (pour tes réflexions) et 'answer' (pour la phrase finale).
        3. TA PHRASE DANS 'answer' DOIT ÊTRE UNE SEULE ET UNIQUE PHRASE, COURTE POUR RENTRER DANS UNE NOTIFICATION PUSH ANDROID.
        4. UTILISE LE TON DE TON PERSONNAGE (Primarque ${context.primarchName}) DANS 'answer'.
        5. NE RÉPÈTE SOUS AUCUN PRÉTEXTE L'UNE DES PHRASES SUIVANTES DÉJÀ PRONONCÉES RÉCEMMENT :
        ${if (context.coachHistory.isEmpty()) "Aucune." else context.coachHistory.joinToString("\n- ", prefix = "- ")}
    """.trimIndent()

    val userPrompt = """
        Voici les informations sur le joueur :
        
        * Légion : ${context.legionName}
        * Primarque : ${context.primarchName}
        * Niveau : ${context.currentLevel}
        * Score actuel : ${context.currentScore}
        * Points avant le prochain rang : ${context.pointsToNextLevel}
        * Points de la semaine (progression 7j) : ${if (context.currentScore - context.score7DaysAgo >= 0) "+" else ""}${context.currentScore - context.score7DaysAgo}
        * Bilan des activités (nom xNombreDeFois (Statut)) :
        ${if (context.activitiesDetails.isEmpty()) "- Aucune activité." else context.activitiesDetails.joinToString("\n        - ", prefix = "- ")}

        Analyse le statut des activités. Les activités avec un mauvais statut nécessitent d'être corrigées. Félicite les bons statuts.
        Génère ton encouragement (UNE SEULE PHRASE) en fonction du bilan.
    """.trimIndent()
    return Pair(systemInstructionText, userPrompt)
}
