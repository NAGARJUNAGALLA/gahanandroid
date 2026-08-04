package com.jcv.mocktests.models

data class Course(
    val sheetId: String = "",
    val title: String = "",
    val fee: Double = 0.0,
    val description: String = ""
)

data class Question(
    val id: Int = 0,
    val text: String = "",
    val options: List<String> = emptyList(),
    val correct: Int = 0
)

enum class QuestionStatus {
    NOT_VISITED, NOT_ANSWERED, ANSWERED, MARKED_FOR_REVIEW, ANSWERED_AND_MARKED
}

data class QuestionState(
    var status: QuestionStatus = QuestionStatus.NOT_VISITED,
    var selectedOption: Int? = null
)
