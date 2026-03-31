package com.example.oto1720.dojo2026.data.network

import kotlinx.coroutines.flow.Flow

/**
 * ネットワーク接続状態を監視するインターフェース。
 *
 * [isOnline] が `true` のときオンライン、`false` のときオフラインを表す。
 * 状態変化があったときのみ値が流れる（distinctUntilChanged）。
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
