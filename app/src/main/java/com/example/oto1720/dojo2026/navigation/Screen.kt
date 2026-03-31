package com.example.oto1720.dojo2026.navigation

sealed class Screen(val route: String) {
    data object Portal : Screen("portal")
    data object Tsundoku : Screen("tsundoku")
    data object Done : Screen("done")

    data class Digest(val articleId: String) : Screen("digest/$articleId") {
        companion object {
            const val ROUTE = "digest/{articleId}"
            const val ARG = "articleId"
            fun createRoute(articleId: String) = "digest/$articleId"
        }
    }

    data class DoneDetail(val articleId: String) : Screen("done_detail/$articleId") {
        companion object {
            const val ROUTE = "done_detail/{articleId}"
            const val ARG = "articleId"
            fun createRoute(articleId: String) = "done_detail/$articleId"
        }
    }

}
