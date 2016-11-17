package com.icapps.niddler.ui.form

import com.icapps.niddler.ui.NiddlerClient
import com.icapps.niddler.ui.NiddlerClientListener
import com.icapps.niddler.ui.adb.ADBBootstrap
import com.icapps.niddler.ui.model.*
import com.icapps.niddler.ui.model.ui.TimelineMessagesTableModel
import com.icapps.niddler.ui.setColumnFixedWidth
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * @author Nicola Verbeeck
 * @date 14/11/16.
 */
class NiddlerWindow : JFrame(), NiddlerClientListener, NiddlerMessageListener {

    private val windowContents = MainWindow()
    private val adbConnection = ADBBootstrap()

    private val messages = MessageContainer(NiddlerMessageBodyParser())
    private var currentDetailMessage: ParsedNiddlerMessage? = null

    fun init() {
        add(windowContents.rootPanel)

        windowContents.messages.model = TimelineMessagesTableModel()
        windowContents.messages.setColumnFixedWidth(0, 90)
        windowContents.messages.setColumnFixedWidth(1, 36)
        windowContents.messages.setColumnFixedWidth(2, 40)
        windowContents.messages.setColumnFixedWidth(3, 400)
        windowContents.messages.tableHeader = null
        windowContents.statusText.text = "<>"
        windowContents.statusBar.border = BorderFactory.createCompoundBorder(windowContents.statusBar.border, EmptyBorder(1, 6, 1, 6))

        windowContents.messages.selectionModel.addListSelectionListener {
            if (windowContents.messages.selectedRowCount == 0) {
                clearDetailPanel()
            } else {
                val selectedRow = windowContents.messages.selectedRow
                val row = (windowContents.messages.model as TimelineMessagesTableModel).getRow(selectedRow)
                showMessageDetails(row)
            }
        }

        clearDetailPanel()
        windowContents.buttonClear.addActionListener {
            messages.clear()
            val model = windowContents.messages.model as TimelineMessagesTableModel
            windowContents.messages.clearSelection()
            model.updateMessages(messages)
        }

        windowContents.connectButton.addActionListener {
            val selection = NiddlerConnectDialog.showDialog(this, adbConnection.bootStrap(), null, null)
            if (selection != null)
                onDeviceSelectionChanged(selection)
        }

        pack()
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                super.windowClosing(e)
                messages.unregisterListener(this@NiddlerWindow)
            }

            override fun windowOpened(e: WindowEvent?) {
                super.windowOpened(e)
                messages.registerListener(this@NiddlerWindow)
            }
        })
        isVisible = true
    }

    private fun onDeviceSelectionChanged(params: NiddlerConnectDialog.ConnectSelection) {
        val ip = if (params.serial != null) {
            adbConnection.extend(params.serial)?.forwardTCPPort(6555, params.port)
            "127.0.0.1"
        } else
            params.ip!!

        initNiddlerOnDevice(ip)
    }

    private var niddlerClient: NiddlerClient? = null

    private fun initNiddlerOnDevice(ip: String) {
        niddlerClient?.close()
        niddlerClient?.unregisterClientListener(this)
        niddlerClient?.unregisterMessageListener(messages)
        messages.clear()
        if (niddlerClient != null) {
            //TODO Remove previous port mapping, this could cause conflicts, to check
        }
        niddlerClient = NiddlerClient(URI.create("ws://$ip:6555"))
        niddlerClient?.registerClientListener(this)
        niddlerClient?.registerMessageListener(messages)
        niddlerClient?.connectBlocking()
    }

    private fun clearDetailPanel() {
        currentDetailMessage = null
        windowContents.detailPanel.removeAll()
        windowContents.detailPanel.add(JLabel("Select a request/response to the body", SwingConstants.CENTER), BorderLayout.CENTER)
        windowContents.detailPanel.revalidate()
        windowContents.splitPane.revalidate()
        windowContents.detailPanel.repaint()
    }

    private fun showEmptyMessageDetails() {
        windowContents.detailPanel.removeAll()
        windowContents.detailPanel.add(JLabel("This request/response has no body", SwingConstants.CENTER), BorderLayout.CENTER)
        windowContents.detailPanel.revalidate()
        windowContents.splitPane.revalidate()
        windowContents.detailPanel.repaint()
    }

    private fun showMessageDetails(message: ParsedNiddlerMessage) {
        if (currentDetailMessage?.messageId == message.messageId)
            return
        currentDetailMessage = message

        windowContents.detailPanel.removeAll()
        if (message.bodyFormat.type == BodyFormatType.FORMAT_JSON) {
            windowContents.detailPanel.add(NiddlerJsonDataPanel(message), BorderLayout.CENTER)
        } else if (message.bodyFormat.type == BodyFormatType.FORMAT_XML) {
            windowContents.detailPanel.add(NiddlerXMLDataPanel(message), BorderLayout.CENTER)
        } else if (message.body.isNullOrBlank()) {
            showEmptyMessageDetails()
            return
        }
        windowContents.splitPane.revalidate()
        windowContents.detailPanel.revalidate()
        windowContents.detailPanel.repaint()
    }

    override fun onConnected() {
        SwingUtilities.invokeLater {
            windowContents.statusText.text = "Connected"
            windowContents.statusText.icon = ImageIcon(NiddlerWindow::class.java.getResource("/ic_connected.png"))
        }
    }

    override fun onDisconnected() {
        SwingUtilities.invokeLater {
            windowContents.statusText.text = "Disconnected"
            windowContents.statusText.icon = ImageIcon(NiddlerWindow::class.java.getResource("/ic_disconnected.png"))
        }
    }

    override fun onMessage(message: ParsedNiddlerMessage) {
        if (message.isControlMessage) {
            handleControlMessage(message)
            return
        }

        SwingUtilities.invokeLater {
            val previousSelection = windowContents.messages.selectedRow
            (windowContents.messages.model as TimelineMessagesTableModel).updateMessages(messages)
            if (previousSelection != -1)
                windowContents.messages.addRowSelectionInterval(previousSelection, previousSelection)
        }
    }

    private fun handleControlMessage(message: ParsedNiddlerMessage) {
        when (message.controlCode) {
            1 -> SwingUtilities.invokeLater {
                windowContents.statusText.text = "Connected to ${message.controlData?.get("serverName")?.asString} (${message.controlData?.get("serverDescription")?.asString})"
                windowContents.statusText.icon = ImageIcon(NiddlerWindow::class.java.getResource("/ic_connected.png"))
            }

            else -> JOptionPane.showMessageDialog(this, "Received unknown control message")
        }

    }

}