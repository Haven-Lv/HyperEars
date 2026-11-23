package dev.hyperears.integration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One paired peer as reported by the headset's own dual-connection list. */
@Serializable
data class HuaweiDualConnectPeer(
    val mac: String,
    val name: String,
    val connected: Boolean,
    val playing: Boolean,
    val preferred: Boolean,
)

/**
 * Dual-connection roster reported by the Huawei SPP `2B 31` command.
 *
 * The roster is device state, not a capability: an empty list simply means the headset has not
 * answered yet. Only adapters that declare this feature receive it.
 */
@Serializable
@SerialName("huawei.dual_connect")
data class HuaweiDualConnectFeatureState(
    val peers: List<HuaweiDualConnectPeer>,
) : DeviceFeatureState {
    @Transient
    override val featureId: String = FEATURE_ID

    companion object {
        const val FEATURE_ID = "huawei.dual_connect"
    }
}

/**
 * Requests that switch the headset between already paired peers.
 *
 * This is a separate request family from [HuaweiControlRequest] so the Pro 3 ANC-level control
 * keeps its own contract and neither family has to grow model-specific branches.
 */
@Serializable
sealed interface HuaweiDualConnectRequest : ControlRequest {
    @Serializable
    @SerialName("huawei.dual_connect.connect")
    data class ConnectPeer(
        val mac: String,
    ) : HuaweiDualConnectRequest

    @Serializable
    @SerialName("huawei.dual_connect.disconnect")
    data class DisconnectPeer(
        val mac: String,
    ) : HuaweiDualConnectRequest
}
