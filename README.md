# CommonsMarkdown
Utilities for parsing common markdown language

Wraps [commonmark-java](https://github.com/commonmark/commonmark-java) behind a stream based element model. No commonmark type is exposed by the api, so
callers depend on `MarkdownUtils` alone.

## Parsing

```java
MarkdownUtils.parse(markdown)
             .get()
             .forEach(element -> element.asHeading()
                                        .ifPresent(heading -> System.out.println(heading.getText())));
```

Each `Element` offers an `asXyz()` accessor returning an `Optional`, so a caller only handles the types it cares about:

`Text` `Code` `CodeBlock` `Heading` `Link` `Image` `Paragraph` `UnorderedList` `OrderedList` `Table` `LineBreak` `ThematicBreak` `Html` `HtmlBlock`
`TaskListMarker` `CustomIdentifier`

Useful details carried by the model: `Text.isBold()` / `isItalic()` / `isStrikethrough()`, `LineBreak.isHard()`, `CodeBlock.getLanguage()`,
`OrderedList.getStartNumber()`, `BasicList.isTight()` and `Table.Column.getAlignment()`. `Element.getSourceLine()` names the line a construct was parsed from,
currently for `Text` and `Link`.

### Options

```java
MarkdownUtils.parse(markdown, options -> options.enableWrapIntoParagraphs()
                                                .enableParseCustomIdTokens());
```

- `enableWrapIntoParagraphs()` — keeps paragraphs as `Paragraph` elements instead of flattening their content into the stream
- `enableParseCustomIdTokens()` — reads a single word within curly braces, like `{GRID}` or `{#anchor}`, as a `CustomIdentifier`. Ordinary braces within the
  content, like a JSON example, are left untouched.

## Supported markdown

Full [CommonMark](https://commonmark.org/) plus the four extensions that make up
[GitHub flavored markdown](https://github.github.com/gfm/):

| GFM extension | Example | Model |
|---|---|---|
| Tables | `\|a\|b\|` | `Table` with `Column.getAlignment()` |
| Strikethrough | `~~text~~` | `Text.isStrikethrough()` |
| Task list items | `- [x] done` | `TaskListMarker.isChecked()` |
| Autolinks | `www.example.org` | `Link` |

Known limits: block quotes and list items are not modelled as own elements, so their content arrives flattened into the surrounding stream. Footnotes are not
parsed.

## Writing

```java
String markdown = MarkdownUtils.builder()
                               .addHeading("Title")
                               .addUnorderedList(Arrays.asList("first", "second"))
                               .addCodeBlock("int value = 1;", "java")
                               .build()
                               .get();
```

The builder writes headings, text, bold, italic, strikethrough, links, images, inline code, code blocks, ordered and unordered lists, task lists, block quotes,
tables, line breaks and thematic breaks. `build().parse()` closes the round trip.

## Build

```cmd
mvn clean install
```
