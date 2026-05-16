package com.example.indentationchecker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.util.TextRange

class IndentationInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
                val lines = document.text.split('\n')
                val style = detectIndentationStyle(lines)

                for ((index, line) in lines.withIndex()) {
                    val indent = leadingWhitespace(line)
                    if (indent.isEmpty()) continue

                    val message = when {
                        indent.contains('\t') && indent.contains(' ') ->
                            "Mixed tabs and spaces in indentation. Use either tabs or spaces consistently."
                        style == IndentStyle.SPACES && indent.contains('\t') ->
                            "Tab found in indentation, but spaces are the project indentation style."
                        style == IndentStyle.TABS && indent.contains(' ') ->
                            "Space found in indentation, but tabs are the project indentation style."
                        else -> null
                    } ?: continue

                    val lineStart = document.getLineStartOffset(index)
                    val lineEnd = lineStart + indent.length
                    val element = file.findElementAt(lineStart) ?: file
                    val relativeRange = TextRange(lineStart - element.textRange.startOffset, lineEnd - element.textRange.startOffset)
                    val quickFix = when {
                        indent.contains('\t') && indent.contains(' ') -> normalizeFix(IndentStyle.SPACES)
                        style == IndentStyle.SPACES && indent.contains('\t') -> normalizeFix(IndentStyle.SPACES)
                        style == IndentStyle.TABS && indent.contains(' ') -> normalizeFix(IndentStyle.TABS)
                        else -> null
                    }
                    val fileFix = when (style) {
                        IndentStyle.SPACES -> normalizeFileFix(IndentStyle.SPACES)
                        IndentStyle.TABS -> normalizeFileFix(IndentStyle.TABS)
                        IndentStyle.MIXED -> normalizeFileFix(IndentStyle.SPACES)
                        else -> null
                    }

                    if (quickFix != null && fileFix != null) {
                        holder.registerProblem(
                            element,
                            relativeRange,
                            message,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            quickFix,
                            fileFix
                        )
                    } else if (quickFix != null) {
                        holder.registerProblem(element, relativeRange, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFix)
                    } else {
                        holder.registerProblem(element, relativeRange, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    }
                }
            }
        }
    }

    private fun detectIndentationStyle(lines: List<String>): IndentStyle {
        var spaceOnlyLines = 0
        var tabOnlyLines = 0

        for (line in lines) {
            val indent = leadingWhitespace(line)
            if (indent.isEmpty()) continue
            if (indent.contains('\t') && indent.contains(' ')) return IndentStyle.MIXED
            if (indent.contains('\t')) tabOnlyLines += 1
            if (indent.contains(' ') && !indent.contains('\t')) spaceOnlyLines += 1
        }

        return when {
            spaceOnlyLines > tabOnlyLines -> IndentStyle.SPACES
            tabOnlyLines > spaceOnlyLines -> IndentStyle.TABS
            spaceOnlyLines > 0 || tabOnlyLines > 0 -> IndentStyle.SPACES
            else -> IndentStyle.UNKNOWN
        }
    }

    private fun leadingWhitespace(line: String): String {
        return line.takeWhile { it == ' ' || it == '\t' }
    }

    private fun normalizeFix(targetStyle: IndentStyle): LocalQuickFix {
        return NormalizeIndentationQuickFix(targetStyle)
    }

    private fun normalizeFileFix(targetStyle: IndentStyle): LocalQuickFix {
        return NormalizeFileIndentationQuickFix(targetStyle)
    }

    private fun normalizeIndentationLine(line: String, targetStyle: IndentStyle): String {
        val indent = leadingWhitespace(line)
        if (indent.isEmpty()) return line
        return convertIndentation(indent, targetStyle) + line.substring(indent.length)
    }

    private fun convertIndentation(indent: String, targetStyle: IndentStyle): String {
        return when (targetStyle) {
            IndentStyle.SPACES -> indent.replace("\t", "    ")
            IndentStyle.TABS -> {
                val spaces = indent.count { it == ' ' }
                val tabs = indent.count { it == '\t' }
                "\t".repeat(spaces / 4) + " ".repeat(spaces % 4) + "\t".repeat(tabs)
            }
            else -> indent
        }
    }

    private class NormalizeIndentationQuickFix(private val targetStyle: IndentStyle) : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Normalize indentation to ${targetStyle.name.lowercase()}"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val file = element.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
            val rangeInElement = descriptor.textRangeInElement
            val startOffset = element.textRange.startOffset + rangeInElement.startOffset
            val endOffset = element.textRange.startOffset + rangeInElement.endOffset
            if (startOffset >= endOffset) return

            val indentText = document.getText(TextRange(startOffset, endOffset))
            val normalizedIndent = convertIndentation(indentText, targetStyle)

            WriteCommandAction.runWriteCommandAction(project) {
                document.replaceString(startOffset, endOffset, normalizedIndent)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }
    }

    private class NormalizeFileIndentationQuickFix(private val targetStyle: IndentStyle) : LocalQuickFix {
        override fun getFamilyName(): String {
            return "Auto-fix indentation to ${targetStyle.name.lowercase()} for this file"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val file = element.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
            val originalText = document.text
            val normalizedText = originalText.split("\n").joinToString("\n") {
                normalizeIndentationLine(it, targetStyle)
            } + if (originalText.endsWith("\n")) "\n" else ""

            if (normalizedText == originalText) return

            WriteCommandAction.runWriteCommandAction(project) {
                document.replaceString(0, document.textLength, normalizedText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }
    }

    private enum class IndentStyle {
        SPACES,
        TABS,
        MIXED,
        UNKNOWN
    }
}
