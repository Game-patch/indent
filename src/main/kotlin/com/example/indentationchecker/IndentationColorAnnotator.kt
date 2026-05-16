package com.example.indentationchecker

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import java.awt.Font

class IndentationColorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiWhiteSpace) return

        val text = element.text
        val indentStart = text.lastIndexOf('\n') + 1
        if (indentStart >= text.length) return

        val indent = text.substring(indentStart)
        if (indent.isEmpty()) return

        var offset = element.textRange.startOffset + indentStart
        for (character in indent) {
            val range = TextRange(offset, offset + 1)
            val attributes = when (character) {
                ' ' -> SPACE_INDENT_ATTRIBUTES
                '\t' -> TAB_INDENT_ATTRIBUTES
                else -> null
            } ?: continue

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(attributes)
                .create()

            offset += 1
        }
    }

    companion object {
        private val SPACE_INDENT_ATTRIBUTES = TextAttributes(
            null,
            JBColor(0xE8F9E8, 0x134713),
            null,
            null,
            Font.PLAIN,
        )

        private val TAB_INDENT_ATTRIBUTES = TextAttributes(
            null,
            JBColor(0xFFF0D6, 0x4F3A00),
            null,
            null,
            Font.PLAIN,
        )
    }
}
