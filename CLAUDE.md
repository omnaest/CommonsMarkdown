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
