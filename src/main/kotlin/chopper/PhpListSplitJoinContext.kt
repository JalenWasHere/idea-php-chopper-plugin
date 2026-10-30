package chopper

import com.intellij.openapi.editor.actions.lists.CommaListSplitJoinContext
import com.intellij.openapi.editor.actions.lists.JoinOrSplit
import com.intellij.openapi.editor.actions.lists.ListWithElements
import com.intellij.openapi.editor.actions.lists.TrailingComma
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.php.config.PhpLanguageFeature
import com.jetbrains.php.config.PhpLanguageLevel
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ExtendsList
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.ImplementsList
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpUseList
import com.jetbrains.php.lang.psi.elements.Statement

/**
 * Split/join for PHP parameter, argument, attribute argument, `implements`/`extends` and closure `use` lists.
 * Arrays stay with PhpStorm's own PhpSplitJoinContext.
 */
class PhpListSplitJoinContext : CommaListSplitJoinContext() {

    override fun extractData(context: PsiElement): ListWithElements? {
        val list = findList(context) ?: return null
        val elements = list.children.filter(::isItem)
        return ListWithElements(list, elements)
    }

    override fun isValidIntermediateElement(data: ListWithElements, element: PsiElement): Boolean =
        element is PsiWhiteSpace || element is PsiComment || isItemPrefix(element)

    // Parenthesised lists open and close on their own line; `implements A, B` only breaks after the keyword.
    override fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
        mode == JoinOrSplit.SPLIT

    // The split tail break is added in addTailReplacementsForSplitting; on join the break is removed.
    override fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean = false

    override fun getHeadBreakJoinReplacement(firstElement: PsiElement): String =
        if (isClassReferenceList(firstElement.parent)) " " else ""

    // The platform breaks before the item's first PSI element; a named argument's label must move with its value.
    override fun addHeadReplacementsForSplitting(
        data: ListWithElements,
        replacements: MutableList<Pair<TextRange, String>>,
        firstElement: PsiElement,
    ) {
        if (prevBreak(data, firstElement) != null) {
            return
        }
        val offset = itemStart(firstElement).textRange.startOffset
        replacements.add(TextRange(offset, offset) to "\n")
    }

    // Neither the platform nor PhpStorm's formatter indents the line holding `)`, so the break carries the indent.
    override fun addTailReplacementsForSplitting(
        data: ListWithElements,
        replacements: MutableList<Pair<TextRange, String>>,
        lastElement: PsiElement,
    ) {
        super.addTailReplacementsForSplitting(data, replacements, lastElement)
        if (isClassReferenceList(data.list) || nextBreak(data, lastElement) != null) {
            return
        }
        val offset = findOffsetForBreakAfter(data, lastElement)
        replacements.add(TextRange(offset, offset) to "\n" + lineIndent(data.list))
    }

    private fun lineIndent(element: PsiElement): String {
        val text = element.containingFile.text
        val lineStart = text.lastIndexOf('\n', element.textRange.startOffset - 1) + 1
        return text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
    }

    // PHP puts the whitespace after `(` and before `)` outside the list, where the platform does not look.
    override fun prevBreak(data: ListWithElements, element: PsiElement): PsiElement? =
        super.prevBreak(data, element) ?: outerBreak(data, element, next = false)

    override fun nextBreak(data: ListWithElements, element: PsiElement): PsiElement? =
        super.nextBreak(data, element) ?: outerBreak(data, element, next = true)

    private fun outerBreak(data: ListWithElements, element: PsiElement, next: Boolean): PsiElement? {
        var current = if (next) element.nextSibling else element.prevSibling
        while (current != null) {
            if (!isValidIntermediateElement(data, current) && !isSeparator(current)) {
                return null
            }
            current = if (next) current.nextSibling else current.prevSibling
        }
        val outside = if (next) data.list.nextSibling else data.list.prevSibling
        return outside?.takeIf { it is PsiWhiteSpace && it.textContains('\n') }
    }

    override fun getTrailingComma(data: ListWithElements, mode: JoinOrSplit, lastElement: PsiElement): TrailingComma {
        if (mode == JoinOrSplit.JOIN) {
            return TrailingComma.REMOVE_IF_EXISTS
        }
        val feature = trailingCommaFeature(data.list) ?: return TrailingComma.IGNORE
        return if (PhpLanguageLevel.current(data.list.project).hasFeature(feature)) {
            TrailingComma.ADD_IF_NOT_EXISTS
        } else {
            TrailingComma.IGNORE
        }
    }

    /** Innermost supported list around the caret, or the list owned by the name/keyword the caret is on. */
    private fun findList(context: PsiElement): PsiElement? {
        var child: PsiElement? = null
        var current: PsiElement? = context
        while (current != null && !isBoundary(current)) {
            if (isSupportedList(current)) {
                return current
            }
            if (child is LeafPsiElement) {
                ownedList(current)?.let { return it }
            }
            child = current
            current = current.parent
        }
        return null
    }

    private fun ownedList(owner: PsiElement): PsiElement? = when (owner) {
        is PhpClass -> listOf(owner.implementsList, owner.extendsList).firstOrNull { it.children.any(::isItem) }
        else -> owner.children.firstOrNull { it is ParameterList }
    }

    private fun isSupportedList(element: PsiElement): Boolean =
        element is ParameterList || element is ImplementsList || element is ExtendsList || element is PhpUseList

    private fun isBoundary(element: PsiElement): Boolean =
        element is ArrayCreationExpression || element is GroupStatement || element is Statement || element is PhpFile

    private fun isClassReferenceList(list: PsiElement?): Boolean = list is ImplementsList || list is ExtendsList

    private fun isItem(element: PsiElement): Boolean = element !is LeafPsiElement && element !is PsiWhiteSpace

    /** Leaves that belong to the next item: a named argument's `name:`, spread `...`, by-reference `&`. */
    private fun isItemPrefix(element: PsiElement): Boolean {
        if (element !is LeafPsiElement) {
            return false
        }
        if (element.textMatches(":") || element.textMatches("...") || element.textMatches("&")) {
            return true
        }
        val next = generateSequence(element.nextSibling) { it.nextSibling }.firstOrNull { it !is PsiWhiteSpace }
        return next is LeafPsiElement && next.textMatches(":")
    }

    private fun itemStart(item: PsiElement): PsiElement {
        var start = item
        var previous = item.prevSibling
        while (previous != null && (previous is PsiWhiteSpace || isItemPrefix(previous))) {
            if (previous !is PsiWhiteSpace) {
                start = previous
            }
            previous = previous.prevSibling
        }
        return start
    }

    private fun trailingCommaFeature(list: PsiElement): PhpLanguageFeature? = when {
        list is PhpUseList -> PhpLanguageFeature.TRAILING_COMMA_IN_CLOSURE_USE_LIST
        list is ParameterList && list.parent is Function -> PhpLanguageFeature.TRAILING_COMMA_IN_PARAMETER_LIST
        list is ParameterList -> PhpLanguageFeature.TRAILING_COMMA_IN_FUNCTION_CALL
        else -> null
    }
}
