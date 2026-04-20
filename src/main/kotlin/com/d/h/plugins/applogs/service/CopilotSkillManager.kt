package com.d.h.plugins.applogs.service
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
@Service(Service.Level.PROJECT)
class CopilotSkillManager(private val project: Project) {
    fun isSkillInstalled(): Boolean {
        val basePath = project.basePath ?: return false
        return Files.exists(skillTargetPath(basePath))
    }
    fun installSkill() {
        val basePath = project.basePath ?: return
        val targetPath = skillTargetPath(basePath)
        Files.createDirectories(targetPath.parent)
        val content = CopilotSkillManager::class.java
            .getResourceAsStream(SKILL_RESOURCE_PATH)
            ?.use { it.readBytes() }
            ?: error("Skill resource not found: $SKILL_RESOURCE_PATH")
        Files.write(targetPath, content)
    }
    private fun skillTargetPath(basePath: String): Path =
        Path.of(basePath, ".github", "skills", "android-logcat-reader", "SKILL.md")
    companion object {
        private const val SKILL_RESOURCE_PATH = "/skills/android-logcat-reader/SKILL.md"
    }
}
