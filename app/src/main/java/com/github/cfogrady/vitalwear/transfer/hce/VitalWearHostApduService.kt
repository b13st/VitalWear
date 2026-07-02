package com.github.cfogrady.vitalwear.transfer.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.github.cfogrady.vitalwear.VitalWearApp
import kotlinx.coroutines.launch
import timber.log.Timber

class VitalWearHostApduService : HostApduService() {

    private val repository: VitalWearHceTransferRepository by lazy {
        VitalWearHceTransferRepository(application as VitalWearApp)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }

        if (VitalWearHceProtocol.isSelectAidApdu(commandApdu)) {
            return VitalWearHceProtocol.buildResponse()
        }

        if (commandApdu.size < 2 || commandApdu[0] != VitalWearHceProtocol.CLA_VITALWEAR) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_DATA)
        }

        val ins = commandApdu[1]
        val data = VitalWearHceProtocol.parseCommandData(commandApdu)

        return try {
            when (ins) {
                VitalWearHceProtocol.INS_NEGOTIATE -> handleNegotiate(data)
                VitalWearHceProtocol.INS_READ_CHUNK -> handleReadChunk(data)
                VitalWearHceProtocol.INS_WRITE_CHUNK -> handleWriteChunk(data)
                VitalWearHceProtocol.INS_COMMIT -> handleCommit()
                else -> VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_FUNC_NOT_SUPPORTED)
            }
        } catch (e: Exception) {
            Timber.e(e, "HCE command 0x%02X failed", ins)
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_INTERNAL_ERROR)
        }
    }

    override fun onDeactivated(reason: Int) {
        // Keep the armed mode until the UI changes mode or the activity exits.
    }

    private fun handleNegotiate(data: ByteArray): ByteArray {
        if (data.size < 2) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }

        val mode = data[0]
        val version = data[1]
        if (version != VitalWearHceProtocol.VERSION_1) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_FUNC_NOT_SUPPORTED)
        }

        // Auto-arm so VBHelper can initiate a transfer without the user first navigating to
        // TransferActivity on the watch.  The existing buttons in TransferActivity still work
        // and take precedence when the session is already armed.
        if (VitalWearHceSessionManager.currentMode() == VitalWearHceSessionManager.Mode.IDLE) {
            when (mode) {
                VitalWearHceProtocol.MODE_WATCH_TO_PHONE -> {
                    // VBHelper wants to READ from the watch — arm a SEND session automatically.
                    val payload = repository.getActiveCharacterPayload()
                        ?: return VitalWearHceProtocol.buildResponse(
                            statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED
                        )
                    VitalWearHceSessionManager.armSend(payload)
                }
                VitalWearHceProtocol.MODE_PHONE_TO_WATCH -> {
                    // VBHelper wants to WRITE to the watch — arm a RECEIVE session automatically.
                    VitalWearHceSessionManager.armReceive()
                }
            }
        }

        val session = VitalWearHceSessionManager.negotiate(mode)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

        val responseData = byteArrayOf(
            VitalWearHceProtocol.VERSION_1,
            mode,
            ((VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE ushr 8) and 0xFF).toByte(),
            (VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE and 0xFF).toByte(),
            ((session.payloadLength ushr 24) and 0xFF).toByte(),
            ((session.payloadLength ushr 16) and 0xFF).toByte(),
            ((session.payloadLength ushr 8) and 0xFF).toByte(),
            (session.payloadLength and 0xFF).toByte(),
        )
        return VitalWearHceProtocol.buildResponse(responseData)
    }

    private fun handleReadChunk(data: ByteArray): ByteArray {
        if (data.size != 4) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }
        val offset =
            ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)
        if (offset < 0) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_P1P2)
        }

        val chunk = VitalWearHceSessionManager.readChunk(offset, VitalWearHceProtocol.DEFAULT_MAX_CHUNK_SIZE)
            ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
        return VitalWearHceProtocol.buildResponse(chunk)
    }

    private fun handleWriteChunk(data: ByteArray): ByteArray {
        if (data.size < 4) {
            return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_LENGTH)
        }
        val offset =
            ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)
        val chunk = data.copyOfRange(4, data.size)
        val accepted = VitalWearHceSessionManager.writeChunk(offset, chunk)
        return if (accepted) {
            VitalWearHceProtocol.buildResponse()
        } else {
            VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_WRONG_P1P2)
        }
    }

    private fun handleCommit(): ByteArray {
        return when (VitalWearHceSessionManager.currentMode()) {
            VitalWearHceSessionManager.Mode.SEND_TO_PHONE -> {
                repository.deleteCurrentCharacterAfterSuccessfulSend()
                VitalWearHceSessionManager.markSuccess()
                VitalWearHceSessionManager.clear(resetStatus = false)
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.RECEIVE_FROM_PHONE -> {
                val payload = VitalWearHceSessionManager.takeReceivedPayload()
                    ?: return VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)

                // Acknowledge COMMIT immediately so the phone-side IsoDep transceive does not time out.
                VitalWearHceSessionManager.clear(resetStatus = false)
                // Import on the application scope: this service is destroyed (and a service-local
                // scope would be cancelled) as soon as the NFC field drops, which happens while
                // the import is still running — the character would arrive but the transfer
                // would be reported as failed.
                (application as VitalWearApp).applicationScope.launch {
                    val importSuccess = runCatching {
                        repository.importCharacter(payload)
                    }.onFailure {
                        Timber.e(it, "Character import from HCE payload failed")
                    }.getOrDefault(false)
                    if (importSuccess) {
                        VitalWearHceSessionManager.markSuccess()
                    } else {
                        VitalWearHceSessionManager.markFailure()
                    }
                }
                VitalWearHceProtocol.buildResponse()
            }

            VitalWearHceSessionManager.Mode.IDLE -> {
                VitalWearHceSessionManager.markFailure()
                VitalWearHceProtocol.buildResponse(statusWord = VitalWearHceProtocol.SW_CONDITIONS_NOT_SATISFIED)
            }
        }
    }
}

