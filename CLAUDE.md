# CommonsMarkdown

Single-class Markdown parsing utility (`org.omnaest.utils.markdown`). Wraps the commonmark library behind a fluent Java API.

## Build

```cmd
mvn clean install
mvn test -Dtest=MyTestClass#myMethod
```

## Architecture

One public facade: `MarkdownUtils` in `org.omnaest.utils.markdown`. Follows the same static-facade-with-inner-interfaces pattern as the rest of the Commons stack.

## Code style

- No Lombok, no `@Slf4j` in main code
- Single entry point — callers import only `MarkdownUtils`

## Key class

- **`MarkdownUtils`** — parses Markdown text; produces structured output consumable as a `CommonsTable` (`TableUtils`) for table extraction, and plain text/HTML via commonmark

## Structural parse options (opt-in)

`MarkdownParseOptions` carries two structural opt-ins next to `enableWrapIntoParagraphs()` / `enableParseCustomIdTokens()`, each an `enableX()` / `enableX(boolean)` / `isX()` triple:

- `enableBlockQuotes()` / `isBlockQuotes()` - a block quote becomes one `BlockQuote` element holding its blocks (paragraphs, lists, headings, code blocks, nested `BlockQuote`s).
- `enableListItems()` / `isListItems()` - every list item becomes a `ListItem` element, so `BasicList.getElements()` holds only `ListItem`s and the item boundaries (and a task item's `TaskListMarker`) stay recoverable.

Both are `ElementWithChildren` (`getElements()` / `getChildren()`), have `Element.asBlockQuote()` / `asListItem()` accessors, and `cloneAndFilter` recurses into their children, so `clearCustomTokens()` and the `MarkdownProcessor` see the content inside them.

**Both default to false, and that is deliberate.** Without them the parse output is exactly what it always was: a quote's blocks arrive as top-level siblings and a list's elements are the flat concatenation of its items' content. Several consumers (MarkdownServer, IMBSApplication, React4J before plan-284) dispatch over the known element kinds, so a wrapper emitted by default would make them silently drop the quoted or itemised content - additive in signature, breaking in behaviour. A consumer opts in only when it handles the wrapper. `MarkdownUtilsTest#testDefaultTreeStaysFlat*` pins the default tree.

## Dependencies (compile scope)

- `commonmark:0.29.0` — CommonMark spec parser
- `commonmark-ext-gfm-tables:0.29.0` — GFM tables
- `commonmark-ext-gfm-strikethrough:0.29.0` — GFM strikethrough (`~~text~~`)
- `commonmark-ext-task-list-items:0.29.0` — GFM task list items (`- [x] done`)
- `commonmark-ext-autolink:0.29.0` — GFM autolinks (bare `www.…` / `https://…` / email)
- `CommonsLangAndIO` — stream/IO utilities
- `CommonsTable` — table model for extracted Markdown tables

Those four extensions are the complete GFM delta over CommonMark, so the parser now
covers the flavour content authors write.

Test scope: `CommonsTest`, `CommonsLog`.
