package me.data_architect.m2mm.data

interface LlmService {
    suspend fun generateEncouragementDynamic(context: GameContext): Result<LlmResult>
}
