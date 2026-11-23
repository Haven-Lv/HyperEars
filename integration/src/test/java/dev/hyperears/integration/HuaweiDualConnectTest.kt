package dev.hyperears.integration

import dev.hyperears.protocol.huawei.HuaweiFreebudsSppCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiDualConnectTest {
    private val adapter = HuaweiFreeClipAdapter()

    @Test
    fun rosterFrameDecodesOnePeer() {
        val device = HuaweiFreebudsSppCodec.parseDualConnectFrame(rosterFrame(0x01, 0x02, 0x01, "手机"))
        assertEquals(CONNECTED, device?.mac)
        assertEquals("手机", device?.name)
        assertTrue(device?.connected == true)
        assertFalse(device?.playing == true)
        assertEquals(1, device?.index)
        assertEquals(2, device?.total)
    }

    @Test
    fun rosterFrameRejectsOtherCommands() {
        val battery = HuaweiFreebudsSppCodec.packet(0x0108, listOf(1 to byteArrayOf(0x40)))
        assertNull(HuaweiFreebudsSppCodec.parseDualConnectFrame(battery))
    }

    @Test
    fun switchCommandsCarryTheActionAndPeerAddress() {
        val connect = HuaweiFreebudsSppCodec.dualConnectCommand(
            HuaweiFreebudsSppCodec.DualConnectAction.CONNECT,
            CONNECTED,
        )
        val frame = HuaweiFreebudsSppCodec.parseFrame(connect)
        assertEquals(HuaweiFreebudsSppCodec.CMD_DUAL_CONNECT_EXECUTE, frame?.command)
        assertEquals(CONNECTED, frame?.params?.get(1)?.joinToString(":") {
            "%02X".format(it.toInt() and 0xFF)
        })

        val disconnect = HuaweiFreebudsSppCodec.dualConnectCommand(
            HuaweiFreebudsSppCodec.DualConnectAction.DISCONNECT,
            CONNECTED,
        )
        assertEquals(CONNECTED, HuaweiFreebudsSppCodec.parseFrame(disconnect)?.params?.get(2)
            ?.joinToString(":") { "%02X".format(it.toInt() and 0xFF) })
    }

    @Test
    fun handshakeQueriesTheRosterForDualConnectModels() {
        val commands = adapter.beginHandshake().commands.map(ByteArray::toList)
        assertTrue(commands.contains(HuaweiFreebudsSppCodec.queryDualConnectDevices.toList()))
    }

    @Test
    fun rosterIsPublishedOnlyAfterEveryAnnouncedRecordArrives() {
        val first = adapter.receive(rosterFrame(0x00, 0x02, 0x01, "手机", PEER_A))
        assertNull(adapter.runtimeState().features.get<HuaweiDualConnectFeatureState>())

        adapter.receive(rosterFrame(0x01, 0x02, 0x00, "电脑", PEER_B))
        val roster = adapter.runtimeState().features.get<HuaweiDualConnectFeatureState>()
        assertEquals(2, roster?.peers?.size)
        assertEquals("手机", roster?.peers?.first()?.name)
        assertEquals("电脑", roster?.peers?.last()?.name)
        assertFalse(first.stateChanged && false)
    }

    @Test
    fun switchRequestsAreAcceptedOnlyForReportedPeers() {
        adapter.receive(rosterFrame(0x00, 0x01, 0x01, "手机"))

        assertTrue(
            adapter.executeControl(HuaweiDualConnectRequest.ConnectPeer("AA:BB:CC:DD:EE:FF")).accepted,
        )
        assertFalse(
            adapter.executeControl(HuaweiDualConnectRequest.ConnectPeer("11:22:33:44:55:66")).accepted,
        )
    }

    @Test
    fun protocolResetClearsTheRoster() {
        adapter.receive(rosterFrame(0x00, 0x01, 0x01, "手机"))
        assertTrue(adapter.runtimeState().features.get<HuaweiDualConnectFeatureState>() != null)

        adapter.resetProtocolSession()
        assertNull(adapter.runtimeState().features.get<HuaweiDualConnectFeatureState>())
    }

    private fun rosterFrame(
        index: Int,
        total: Int,
        connection: Int,
        name: String,
        mac: String = CONNECTED,
    ): ByteArray =
        HuaweiFreebudsSppCodec.packet(
            HuaweiFreebudsSppCodec.CMD_DUAL_CONNECT_ENUMERATE,
            listOf(
                2 to byteArrayOf(total.toByte()),
                3 to byteArrayOf(index.toByte()),
                4 to mac.split(":").map { it.toInt(16).toByte() }.toByteArray(),
                5 to byteArrayOf(connection.toByte()),
                7 to byteArrayOf(0x00),
                8 to byteArrayOf(0x01),
                9 to name.toByteArray(Charsets.UTF_8),
            ),
        )

    private companion object {
        const val CONNECTED = "AA:BB:CC:DD:EE:FF"
        const val PEER_A = "AA:BB:CC:DD:EE:FF"
        const val PEER_B = "11:22:33:44:55:66"
    }
}
