package chopper

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.config.PhpLanguageLevel
import com.jetbrains.php.config.PhpProjectConfigurationFacade

class PhpListSplitJoinContextTest : BasePlatformTestCase() {

    // The light test project defaults to PHP 5.6.
    override fun setUp() {
        super.setUp()
        setLanguageLevel(PhpLanguageLevel.PHP840)
    }

    fun testSplitsParameterListWithTrailingComma() = assertSplit(
        "<?php\nfunction f(int \$a, <caret>string \$b = 'x') {}\n",
        "<?php\nfunction f(\n    int \$a,\n    string \$b = 'x',\n) {}\n",
    )

    fun testJoinsParameterListAndDropsTrailingComma() = assertJoin(
        "<?php\nfunction f(\n    int \$a,\n    <caret>string \$b,\n) {}\n",
        "<?php\nfunction f(int \$a, string \$b) {}\n",
    )

    fun testSplitsPromotedConstructorParameters() = assertSplit(
        "<?php\nclass A {\n    public function __construct(private int \$a, <caret>#[X] private string \$b) {}\n}\n",
        "<?php\nclass A {\n    public function __construct(\n        private int \$a,\n" +
            "        #[X] private string \$b,\n    ) {}\n}\n",
    )

    fun testKeepsNamedArgumentLabelWithItsValue() = assertSplit(
        "<?php\nfoo(<caret>b: 1, c: 2);\n",
        "<?php\nfoo(\n    b: 1,\n    c: 2,\n);\n",
    )

    fun testJoinsNamedArguments() = assertJoin(
        "<?php\nfoo(\n    b: 1,\n    <caret>c: 2,\n);\n",
        "<?php\nfoo(b: 1, c: 2);\n",
    )

    fun testSplitsMethodCallFromCaretOnMethodName() = assertSplit(
        "<?php\n\$x->ba<caret>r(1, 2);\n",
        "<?php\n\$x->bar(\n    1,\n    2,\n);\n",
    )

    fun testSplitsSpreadArgument() = assertSplit(
        "<?php\nfoo(<caret>\$a, ...\$rest);\n",
        "<?php\nfoo(\n    \$a,\n    ...\$rest,\n);\n",
    )

    fun testSplitsAttributeArguments() = assertSplit(
        "<?php\n#[Route(<caret>path: '/x', name: 'x')]\nfunction g() {}\n",
        "<?php\n#[Route(\n    path: '/x',\n    name: 'x',\n)]\nfunction g() {}\n",
    )

    fun testSplitsImplementsListWithoutTrailingComma() = assertSplit(
        "<?php\nclass A implements <caret>B, C {}\n",
        "<?php\nclass A implements\n    B,\n    C {}\n",
    )

    fun testJoinsImplementsList() = assertJoin(
        "<?php\nclass A implements\n    B,\n    <caret>C {}\n",
        "<?php\nclass A implements B, C {}\n",
    )

    fun testSplitsClosureUseList() = assertSplit(
        "<?php\n\$f = function () use (<caret>\$a, &\$b) {};\n",
        "<?php\n\$f = function () use (\n    \$a,\n    &\$b,\n) {};\n",
    )

    fun testOmitsTrailingCommaBelowPhp80ForParameters() {
        setLanguageLevel(PhpLanguageLevel.PHP740)
        assertSplit(
            "<?php\nfunction f(int \$a, <caret>string \$b) {}\n",
            "<?php\nfunction f(\n    int \$a,\n    string \$b\n) {}\n",
        )
    }

    fun testSplitsInnermostArgumentListOnly() = assertSplit(
        "<?php\nfoo(\$a, bar(<caret>\$b, \$c));\n",
        "<?php\nfoo(\$a, bar(\n    \$b,\n    \$c,\n));\n",
    )

    // PhpStorm's own array split keeps `[` and `]` on the item lines.
    fun testLeavesArraysToPhpStorm() = assertSplitIntention(
        "Split comma-separated values into multiple lines",
        "<?php\nfoo(\$a, [<caret>1, 2]);\n",
        "<?php\nfoo(\$a, [1,\n    2]);\n",
    )

    fun testIgnoresCaretInsideFunctionBody() {
        myFixture.configureByText("a.php", "<?php\nfunction f(\$a, \$b) {\n    \$x = <caret>1;\n}\n")
        val context = PhpListSplitJoinContext()
        assertNull(context.extractData(myFixture.file.findElementAt(myFixture.caretOffset)!!))
    }

    private fun assertSplit(before: String, after: String) = assertIntention(SPLIT, before, after)

    private fun assertSplitIntention(text: String, before: String, after: String) {
        assertEmpty(myFixture.apply { configureByText("a.php", before) }.filterAvailableIntentions(SPLIT))
        assertIntention(text, before, after)
    }

    private fun assertJoin(before: String, after: String) = assertIntention(JOIN, before, after)

    private fun assertIntention(text: String, before: String, after: String) {
        myFixture.configureByText("a.php", before)
        myFixture.launchAction(myFixture.findSingleIntention(text))
        assertEquals(after, myFixture.editor.document.text)
    }

    private fun setLanguageLevel(level: PhpLanguageLevel) {
        val facade = PhpProjectConfigurationFacade.getInstance(project)
        val previous = facade.languageLevel
        facade.languageLevel = level
        com.intellij.openapi.util.Disposer.register(testRootDisposable) { facade.languageLevel = previous }
    }

    private companion object {
        const val SPLIT = "Put comma-separated elements on multiple lines"
        const val JOIN = "Put comma-separated elements on one line"
    }
}
