package com.d.h.plugins.applogs.ui

import com.d.h.plugins.applogs.model.ActiveLogSessionView
import com.d.h.plugins.applogs.model.AppScreenshotsSnapshot
import com.d.h.plugins.applogs.model.ConnectedDeviceView
import com.d.h.plugins.applogs.model.LogSessionsSnapshot
import com.d.h.plugins.applogs.service.APP_LOGS_STATE_TOPIC
import com.d.h.plugins.applogs.service.APP_SCREENSHOTS_STATE_TOPIC
import com.d.h.plugins.applogs.service.AppLogsStateListener
import com.d.h.plugins.applogs.service.AppScreenshotsManager
import com.d.h.plugins.applogs.service.AppScreenshotsStateListener
import com.d.h.plugins.applogs.service.CopilotSkillManager
import com.d.h.plugins.applogs.service.LogSessionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.nio.file.Path
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.JList
import javax.swing.SwingConstants

class AppLogsToolWindowPanel(
    private val project: Project,
) : JPanel(BorderLayout(0, 12)), Disposable {
    private val logSessionManager = project.service<LogSessionManager>()
    private val screenshotsManager = project.service<AppScreenshotsManager>()
    private val copilotSkillManager = project.service<CopilotSkillManager>()
    private val uiOrientation = ComponentOrientation.getOrientation(Locale.getDefault())

    private val statusBadge = JBLabel()
    private val statusMessageLabel = JBLabel()
    private val recordToggleLabel = JBLabel("Record every Android Run/Debug session")
    private val recordToggleButton = JToggleButton()
    private val sessionsCountBadge = JBLabel()
    private val sessionsSummaryLabel = JBLabel()
    private val openFolderButton = JButton("Open Folder", AllIcons.Nodes.Folder)
    private val deleteLogsButton = JButton("Delete Logs", AllIcons.Actions.GC)

    private val skillInstallButton = JButton("Install Copilot Skill", AllIcons.Vcs.Branch)
    private val skillStatusBadge = JBLabel()

    private val screenshotsStatusBadge = JBLabel()
    private val screenshotsStatusMessageLabel = JBLabel()
    private val screenshotDeviceLabel = JBLabel("Connected device")
    private val screenshotDeviceContainer = JPanel(BorderLayout())
    private val screenshotDeviceValueLabel = JBLabel()
    private val screenshotDeviceComboBox = JComboBox<ConnectedDeviceView>()
    private val screenshotFolderLabel = JBLabel("Target folder")
    private val screenshotFolderValueLabel = JBLabel()
    private val chooseFolderButton = JButton("Choose Folder", AllIcons.Nodes.Folder)
    private val captureScreenshotButton = JButton("Capture Screenshot")
    private val deleteScreenshotsButton = JButton("Delete Screenshots", AllIcons.Actions.GC)

    private var applyingSnapshot = false
    private var applyingDeviceSelection = false
    private var selectedScreenshotSerial: String? = null
    private var screenshotOperationInProgress = false
    private var latestScreenshotSnapshot: AppScreenshotsSnapshot? = null

    init {
        buildUi()
        bindActions()
        bindState()
        applyLogSnapshot(logSessionManager.snapshot())
        applyScreenshotSnapshot(screenshotsManager.snapshot())
        screenshotsManager.refreshDevices()
        updateSkillPresentation()
    }

    override fun dispose() = Unit

    private fun buildUi() {
        background = baseBackground()
        border = JBUI.Borders.empty(16)

        configureStaticStyles()
        applyComponentOrientation(uiOrientation)

        add(buildCardsPanel(), BorderLayout.NORTH)
    }

    private fun bindActions() {
        recordToggleButton.addActionListener {
            if (!applyingSnapshot) {
                logSessionManager.setEnabled(recordToggleButton.isSelected)
            }
        }

        openFolderButton.addActionListener {
            logSessionManager.openLogsDirectoryInFileManager()
        }

        deleteLogsButton.addActionListener {
            val confirmation = Messages.showYesNoDialog(
                project,
                "Delete all files inside AppLogs and stop any active recording sessions?",
                "Delete App Logs",
                Messages.getQuestionIcon(),
            )
            if (confirmation == Messages.YES) {
                logSessionManager.deleteAllLogs()
            }
        }

        skillInstallButton.addActionListener {
            copilotSkillManager.installSkill()
            updateSkillPresentation()
        }

        screenshotDeviceComboBox.addActionListener {
            if (!applyingDeviceSelection) {
                selectedScreenshotSerial = (screenshotDeviceComboBox.selectedItem as? ConnectedDeviceView)?.serialNumber
            }
        }

        chooseFolderButton.addActionListener {
            chooseTargetFolder()
        }

        captureScreenshotButton.addActionListener {
            setScreenshotOperationInProgress(true)
            screenshotsManager.captureScreenshot(selectedScreenshotSerial) {
                setScreenshotOperationInProgress(false)
            }
        }

        deleteScreenshotsButton.addActionListener {
            val confirmation = Messages.showYesNoDialog(
                project,
                "Delete all App Screenshots files created by this plugin inside the selected target folder?",
                "Delete App Screenshots",
                Messages.getQuestionIcon(),
            )
            if (confirmation == Messages.YES) {
                setScreenshotOperationInProgress(true)
                screenshotsManager.deletePluginScreenshots {
                    setScreenshotOperationInProgress(false)
                }
            }
        }
    }

    private fun bindState() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(APP_LOGS_STATE_TOPIC, AppLogsStateListener { snapshot ->
            ApplicationManager.getApplication().invokeLater(
                { applyLogSnapshot(snapshot) },
                { project.isDisposed },
            )
        })
        connection.subscribe(APP_SCREENSHOTS_STATE_TOPIC, AppScreenshotsStateListener { snapshot ->
            ApplicationManager.getApplication().invokeLater(
                { applyScreenshotSnapshot(snapshot) },
                { project.isDisposed },
            )
        })
    }

    private fun applyLogSnapshot(snapshot: LogSessionsSnapshot) {
        applyingSnapshot = true
        recordToggleButton.isSelected = snapshot.enabled
        updateRecordTogglePresentation(snapshot.enabled)
        applyingSnapshot = false

        updateStatusPresentation(snapshot.enabled, snapshot.activeSessions.size)
        updateSessionsSummary(snapshot.activeSessions)
        openFolderButton.isEnabled = snapshot.logsDirectory != null
        openFolderButton.toolTipText = snapshot.logsDirectory?.toString()
        deleteLogsButton.isEnabled = snapshot.logsDirectory != null
    }

    private fun applyScreenshotSnapshot(snapshot: AppScreenshotsSnapshot) {
        latestScreenshotSnapshot = snapshot
        updateScreenshotsStatusPresentation(snapshot)
        updateScreenshotDevicePresentation(snapshot.connectedDevices)
        updateScreenshotFolderPresentation(snapshot.targetDirectory)
        updateScreenshotActionPresentation(snapshot)
    }

    private fun configureStaticStyles() {
        statusMessageLabel.foreground = secondaryTextColor()
        statusMessageLabel.horizontalAlignment = SwingConstants.LEADING
        recordToggleLabel.font = recordToggleLabel.font.deriveFont(Font.BOLD)
        recordToggleLabel.horizontalAlignment = SwingConstants.LEADING
        sessionsSummaryLabel.foreground = secondaryTextColor()
        sessionsSummaryLabel.horizontalAlignment = SwingConstants.LEADING
        recordToggleButton.font = recordToggleButton.font.deriveFont(Font.BOLD)
        recordToggleButton.isFocusPainted = false
        recordToggleButton.preferredSize = JBUI.size(64, 30)

        screenshotsStatusMessageLabel.foreground = secondaryTextColor()
        screenshotsStatusMessageLabel.horizontalAlignment = SwingConstants.LEADING
        screenshotDeviceLabel.font = screenshotDeviceLabel.font.deriveFont(Font.BOLD)
        screenshotDeviceLabel.horizontalAlignment = SwingConstants.LEADING
        screenshotFolderLabel.font = screenshotFolderLabel.font.deriveFont(Font.BOLD)
        screenshotFolderLabel.horizontalAlignment = SwingConstants.LEADING
        screenshotFolderValueLabel.foreground = secondaryTextColor()
        screenshotFolderValueLabel.horizontalAlignment = SwingConstants.LEADING
        screenshotDeviceValueLabel.foreground = secondaryTextColor()
        screenshotDeviceValueLabel.horizontalAlignment = SwingConstants.LEADING
        screenshotDeviceContainer.isOpaque = false

        screenshotDeviceComboBox.isFocusable = false
        screenshotDeviceComboBox.maximumRowCount = 8
        screenshotDeviceComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? ConnectedDeviceView)?.let(::presentDevice) ?: ""
                return this
            }
        }

        listOf(
            openFolderButton,
            deleteLogsButton,
            recordToggleButton,
            chooseFolderButton,
            captureScreenshotButton,
            deleteScreenshotsButton,
        ).forEach { button ->
            button.iconTextGap = JBUI.scale(6)
            button.preferredSize = Dimension(button.preferredSize.width, button.preferredSize.height + JBUI.scale(2))
        }
    }

    private fun buildCardsPanel(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(buildHeaderCard())
        add(Box.createVerticalStrut(JBUI.scale(12)))
        add(buildScreenshotsCard())
    }

    private fun buildHeaderCard(): JPanel {
        val titleLabel = JBLabel("App Logs").apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 5f)
            horizontalAlignment = SwingConstants.LEADING
        }

        val headerTextPanel = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
        }

        val headerRow = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(headerTextPanel, BorderLayout.CENTER)
            add(statusBadge, BorderLayout.LINE_END)
        }

        val recordRow = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(recordToggleLabel, BorderLayout.CENTER)
            add(recordToggleButton, BorderLayout.LINE_END)
        }

        val actionsPanel = JPanel(GridLayout(1, 2, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(openFolderButton)
            add(deleteLogsButton)
        }

        val skillRow = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            val skillLabel = JBLabel("GitHub Copilot Skill").apply {
                font = font.deriveFont(Font.BOLD)
                horizontalAlignment = SwingConstants.LEADING
            }
            add(skillLabel, BorderLayout.CENTER)
            add(buildSkillActionWidget(), BorderLayout.LINE_END)
        }

        val contentPanel = JPanel().apply {
            isOpaque = false
            border = JBUI.Borders.empty(12)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(fullWidthRow(headerRow))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(statusMessageLabel))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(recordRow))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(actionsPanel))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(skillRow))
        }

        return buildCard(contentPanel)
    }

    private fun buildSkillActionWidget(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(skillInstallButton, BorderLayout.WEST)
        add(skillStatusBadge, BorderLayout.EAST)
    }

    private fun updateSkillPresentation() {
        val installed = copilotSkillManager.isSkillInstalled()
        skillInstallButton.isVisible = !installed
        skillStatusBadge.isVisible = installed
        if (installed) {
            updatePill(skillStatusBadge, "✓ Installed", successColor)
        }
    }

    private fun buildScreenshotsCard(): JPanel {
        val titleLabel = JBLabel("App Screenshots").apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 5f)
            horizontalAlignment = SwingConstants.LEADING
        }

        val headerRow = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.CENTER)
            add(screenshotsStatusBadge, BorderLayout.LINE_END)
        }

        val deviceSection = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(fullWidthRow(screenshotDeviceLabel))
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(fullWidthRow(screenshotDeviceContainer))
        }

        val targetFolderRow = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(screenshotFolderValueLabel, BorderLayout.CENTER)
            add(chooseFolderButton, BorderLayout.LINE_END)
        }

        val targetFolderSection = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(fullWidthRow(screenshotFolderLabel))
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(fullWidthRow(targetFolderRow))
        }

        val actionsPanel = JPanel(GridLayout(1, 2, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(captureScreenshotButton)
            add(deleteScreenshotsButton)
        }

        val contentPanel = JPanel().apply {
            isOpaque = false
            border = JBUI.Borders.empty(12)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(fullWidthRow(headerRow))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(screenshotsStatusMessageLabel))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(deviceSection))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(targetFolderSection))
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(fullWidthRow(actionsPanel))
        }

        return buildCard(contentPanel)
    }

    private fun buildCard(contentPanel: JPanel): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = cardBackground()
        border = createCardBorder()
        add(JPanel().apply {
            background = accentColor
            preferredSize = JBUI.size(4, 0)
        }, BorderLayout.LINE_START)
        add(contentPanel, BorderLayout.CENTER)
    }

    private fun createCardBorder() = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(cardBorderColor, 1, true),
        JBUI.Borders.empty(12),
    )

    private fun updateStatusPresentation(enabled: Boolean, activeCount: Int) {
        when {
            !enabled -> {
                updatePill(statusBadge, "Off", mutedColor)
                statusMessageLabel.text = wrapText("Recording is off. Turn it on before the next Android Run/Debug session.")
            }
            activeCount == 0 -> {
                updatePill(statusBadge, "Ready", accentColor)
                statusMessageLabel.text = wrapText("Ready to capture the next Android Run/Debug session.")
            }
            activeCount == 1 -> {
                updatePill(statusBadge, "Live", successColor)
                statusMessageLabel.text = wrapText("Recording 1 active session.")
            }
            else -> {
                updatePill(statusBadge, "Live", successColor)
                statusMessageLabel.text = wrapText("Recording $activeCount active sessions.")
            }
        }

        updatePill(
            sessionsCountBadge,
            activeCount.toString(),
            if (activeCount > 0) successColor else mutedColor,
        )
    }

    private fun updateSessionsSummary(activeSessions: List<ActiveLogSessionView>) {
        val summary = when (activeSessions.size) {
            0 -> "No active sessions"
            1 -> "${activeSessions.first().deviceName} • ${activeSessions.first().packageName}"
            else -> activeSessions.joinToString(" • ") { it.deviceName }
        }
        sessionsSummaryLabel.text = wrapText(summary)
        sessionsSummaryLabel.toolTipText = activeSessions
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "<br/>", prefix = "<html>", postfix = "</html>") { session ->
                StringUtil.escapeXmlEntities("${session.deviceName} • ${session.packageName} • ${session.logFile.fileName}")
            }
    }

    private fun updateScreenshotsStatusPresentation(snapshot: AppScreenshotsSnapshot) {
        when {
            snapshot.isLoadingDevices -> {
                updatePill(screenshotsStatusBadge, "Checking", accentColor)
                screenshotsStatusMessageLabel.text = wrapText("Checking connected Android devices.")
            }
            !snapshot.adbAvailable -> {
                updatePill(screenshotsStatusBadge, "ADB", mutedColor)
                screenshotsStatusMessageLabel.text = wrapText("ADB is not available in the current Android SDK.")
            }
            snapshot.connectedDevices.isEmpty() -> {
                updatePill(screenshotsStatusBadge, "No Device", mutedColor)
                screenshotsStatusMessageLabel.text = wrapText("Connect an Android device to capture screenshots.")
            }
            snapshot.targetDirectory == null -> {
                updatePill(screenshotsStatusBadge, "Setup", accentColor)
                screenshotsStatusMessageLabel.text = wrapText("Choose a target folder on this computer to enable screenshots.")
            }
            snapshot.connectedDevices.size == 1 -> {
                updatePill(screenshotsStatusBadge, "Ready", successColor)
                screenshotsStatusMessageLabel.text = wrapText("Ready to capture from ${presentDevice(snapshot.connectedDevices.first())}.")
            }
            else -> {
                updatePill(screenshotsStatusBadge, "Ready", successColor)
                screenshotsStatusMessageLabel.text = wrapText("Ready to capture from ${snapshot.connectedDevices.size} connected devices.")
            }
        }
    }

    private fun updateScreenshotDevicePresentation(connectedDevices: List<ConnectedDeviceView>) {
        val selectedDevice = connectedDevices.firstOrNull { it.serialNumber == selectedScreenshotSerial } ?: connectedDevices.firstOrNull()
        selectedScreenshotSerial = selectedDevice?.serialNumber

        screenshotDeviceContainer.removeAll()
        if (connectedDevices.size > 1) {
            applyingDeviceSelection = true
            screenshotDeviceComboBox.model = DefaultComboBoxModel(connectedDevices.toTypedArray())
            screenshotDeviceComboBox.selectedItem = selectedDevice
            applyingDeviceSelection = false
            screenshotDeviceContainer.add(screenshotDeviceComboBox, BorderLayout.CENTER)
        } else {
            screenshotDeviceValueLabel.text = wrapText(selectedDevice?.let(::presentDevice) ?: "No connected devices")
            screenshotDeviceValueLabel.toolTipText = selectedDevice?.serialNumber
            screenshotDeviceContainer.add(screenshotDeviceValueLabel, BorderLayout.CENTER)
        }
        screenshotDeviceContainer.revalidate()
        screenshotDeviceContainer.repaint()
    }

    private fun updateScreenshotFolderPresentation(targetDirectory: Path?) {
        screenshotFolderValueLabel.text = wrapText(targetDirectory?.toString() ?: "No target folder selected")
        screenshotFolderValueLabel.toolTipText = targetDirectory?.toString()
    }

    private fun updateScreenshotActionPresentation(snapshot: AppScreenshotsSnapshot) {
        val hasTargetFolder = snapshot.targetDirectory != null
        val hasConnectedDevice = snapshot.adbAvailable && snapshot.connectedDevices.isNotEmpty()

        chooseFolderButton.isEnabled = !screenshotOperationInProgress
        captureScreenshotButton.isEnabled = !screenshotOperationInProgress && hasTargetFolder && hasConnectedDevice && !snapshot.isLoadingDevices
        deleteScreenshotsButton.isEnabled = !screenshotOperationInProgress && hasTargetFolder && snapshot.pluginScreenshotCount > 0
        screenshotDeviceComboBox.isEnabled = !screenshotOperationInProgress
        captureScreenshotButton.text = if (screenshotOperationInProgress) "Working..." else "Capture Screenshot"
    }

    private fun chooseTargetFolder() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Choose App Screenshots Folder"
            description = "Choose where screenshots from connected Android devices should be saved on this computer."
        }
        val initialDirectory = latestScreenshotSnapshot?.targetDirectory
            ?.let { path -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) }
        val selectedDirectory = FileChooser.chooseFile(descriptor, project, initialDirectory) ?: return
        screenshotsManager.setTargetDirectory(Path.of(selectedDirectory.path))
    }

    private fun setScreenshotOperationInProgress(inProgress: Boolean) {
        screenshotOperationInProgress = inProgress
        latestScreenshotSnapshot?.let(::updateScreenshotActionPresentation)
    }

    private fun updateRecordTogglePresentation(enabled: Boolean) {
        val tone = if (enabled) accentColor else mutedColor
        recordToggleButton.text = if (enabled) "On" else "Off"
        recordToggleButton.foreground = tone
        recordToggleButton.background = toneBackground(tone)
        recordToggleButton.isOpaque = true
        recordToggleButton.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(toneBorder(tone), 1, true),
            JBUI.Borders.empty(4, 10),
        )
    }

    private fun updatePill(label: JBLabel, text: String, tone: Color) {
        label.text = text
        label.foreground = tone
        label.background = toneBackground(tone)
        label.isOpaque = true
        label.horizontalAlignment = SwingConstants.CENTER
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(toneBorder(tone), 1, true),
            JBUI.Borders.empty(4, 10),
        )
    }

    private fun presentDevice(device: ConnectedDeviceView): String {
        return if (device.displayName == device.serialNumber) {
            device.serialNumber
        } else {
            "${device.displayName} • ${device.serialNumber}"
        }
    }

    private fun wrapText(text: String): String {
        val alignment = if (uiOrientation.isLeftToRight) "left" else "right"
        val escapedText = StringUtil.escapeXmlEntities(text)
        return "<html><div style='text-align:$alignment;'>$escapedText</div></html>"
    }

    private fun fullWidthRow(component: Component): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(component, BorderLayout.CENTER)
    }

    private fun baseBackground(): Color = UIUtil.getPanelBackground()

    private fun cardBackground(): Color = if (isDarkTheme()) {
        ColorUtil.mix(baseBackground(), Color.WHITE, 0.06)
    } else {
        ColorUtil.mix(baseBackground(), Color.BLACK, 0.02)
    }

    private fun toneBackground(tone: Color): Color = if (isDarkTheme()) {
        ColorUtil.mix(cardBackground(), tone, 0.22)
    } else {
        ColorUtil.mix(cardBackground(), tone, 0.1)
    }

    private fun toneBorder(tone: Color): Color = if (isDarkTheme()) {
        ColorUtil.mix(cardBorderColor, tone, 0.6)
    } else {
        ColorUtil.mix(cardBorderColor, tone, 0.35)
    }

    private fun secondaryTextColor(): Color = UIUtil.getContextHelpForeground()

    private fun isDarkTheme(): Boolean = ColorUtil.isDark(baseBackground())

    companion object {
        private val accentColor = JBColor(Color(0x0B6BD3), Color(0x79B8FF))
        private val successColor = JBColor(Color(0x1F8B4C), Color(0x7BD89A))
        private val mutedColor = JBColor(Color(0x5F6B7A), Color(0x9AA7B0))
        private val cardBorderColor = JBColor(Color(0xD7DDE7), Color(0x3F4654))
    }
}
