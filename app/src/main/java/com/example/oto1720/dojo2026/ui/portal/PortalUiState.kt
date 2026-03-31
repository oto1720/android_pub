package com.example.oto1720.dojo2026.ui.portal

sealed interface PortalEvent {
    /** 積読スロットが満杯で追加できなかった */
    data object SlotFull : PortalEvent

    /** 記事を積読スロットに追加した */
    data object AddedToTsundoku : PortalEvent

    /** オフラインまたは API 失敗時にキャッシュを表示していることを通知 */
    data object ShowOfflineMessage : PortalEvent
}
