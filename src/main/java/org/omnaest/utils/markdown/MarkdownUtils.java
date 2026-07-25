/*******************************************************************************
 * Copyright 2021 Danny Kunz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.omnaest.utils.markdown;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.omnaest.utils.ConsumerUtils;
import org.omnaest.utils.EnumUtils;
import org.omnaest.utils.FileUtils;
import org.omnaest.utils.MapperUtils;
import org.omnaest.utils.MatcherUtils;
import org.omnaest.utils.PredicateUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.markdown.MarkdownUtils.Table.Column;
import org.omnaest.utils.markdown.MarkdownUtils.Table.Row;

/**
 * Utilities around the markdown format of https://commonmark.org/
 * 
 * @see #parse(String)
 * @author omnaest
 */
public class MarkdownUtils
{
    /**
     * A custom id token is a single word wrapped into curly braces, like <code>{GRID}</code> or <code>{#anchor}</code>. The token is deliberately narrow so that
     * ordinary curly braces within the content, like a JSON example, are left untouched instead of being swallowed as a {@link CustomIdentifier}.
     *
     * @see MarkdownParseOptions#enableParseCustomIdTokens()
     */
    private static final String CUSTOM_ID_TOKEN_REGEX = "\\{(#?[a-zA-Z0-9_.\\-]+)\\}";

    /**
     * Clones the given child {@link Element}s and keeps only those matching the given inclusion filter.
     *
     * @see Element#cloneAndFilter(Predicate)
     * @param elements
     * @param inclusionFilter
     * @return
     */
    @SuppressWarnings("unchecked")
    private static <E extends Element> List<E> cloneAndFilterElements(List<E> elements, Predicate<Element> inclusionFilter)
    {
        return Optional.ofNullable(elements)
                       .orElse(Collections.emptyList())
                       .stream()
                       .map(element -> (Optional<E>) element.cloneAndFilter(inclusionFilter))
                       .filter(PredicateUtils.filterNonEmptyOptional())
                       .map(MapperUtils.mapOptionalToValue())
                       .collect(Collectors.toList());
    }

    private static class MarkdownParsedDocumentImpl implements MarkdownParsedDocument
    {
        private final List<Element> elements;

        private MarkdownParsedDocumentImpl(List<Element> elements)
        {
            this.elements = elements;
        }

        @Override
        public Stream<Element> get()
        {
            return this.elements.stream();
        }

        @Override
        public <E extends Element> Optional<E> findFirst(Class<E> elementType)
        {
            return this.get()
                       .filter(PredicateUtils.matchesType(elementType))
                       .map(MapperUtils.identityCast(elementType))
                       .findFirst()
                       .flatMap(element -> element.as(elementType));
        }

        @Override
        public <E extends Element> Stream<E> getAndFilter(Class<E> elementType)
        {
            return this.get()
                       .filter(PredicateUtils.matchesType(elementType))
                       .map(MapperUtils.identityCast(elementType));
        }

        @Override
        public MarkdownProcessor newProcessor()
        {
            MarkdownParsedDocument document = this;
            return new MarkdownProcessorImpl(document);
        }

        @Override
        public MarkdownParsedDocument clearCustomTokens()
        {
            return new MarkdownParsedDocumentImpl(cloneAndFilterElements(this.elements, element -> !element.asCustomIdentifier()
                                                                                                          .isPresent()));
        }
    }

    private static class MarkdownProcessorImpl extends MarkdownSubProcessorImpl implements MarkdownProcessor
    {
        private final MarkdownParsedDocument document;

        public MarkdownProcessorImpl(MarkdownParsedDocument document)
        {
            this.document = document;
        }

        @Override
        public MarkdownParsedDocument process()
        {
            this.process(this.document.get()
                                      .collect(Collectors.toList()));
            return this.document;
        }

        @Override
        public <E extends Element> MarkdownProcessorImpl addVisitor(Class<E> elementType, Consumer<E> elementConsumer)
        {
            super.addVisitor(elementType, elementConsumer);
            return this;
        }

        @Override
        public <E extends Element> MarkdownProcessorImpl addVisitor(Class<E> elementType, BiConsumer<E, MarkdownProcessorControl> elementConsumer)
        {

            super.addVisitor(elementType, elementConsumer);
            return this;
        }

    }

    private static class MarkdownSubProcessorImpl implements MarkdownSubProcessor
    {
        private final Map<Class<? extends Element>, BiConsumer<Element, MarkdownProcessorControl>> elementTypeToConsumer = new HashMap<>();

        @Override
        public <E extends Element> MarkdownSubProcessorImpl addVisitor(Class<E> elementType, Consumer<E> elementConsumer)
        {
            return this.addVisitor(elementType, (element, control) -> elementConsumer.accept(element));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <E extends Element> MarkdownSubProcessorImpl addVisitor(Class<E> elementType, BiConsumer<E, MarkdownProcessorControl> elementConsumer)
        {
            this.elementTypeToConsumer.put(elementType, (BiConsumer<Element, MarkdownProcessorControl>) elementConsumer);
            return this;
        }

        protected void process(List<Element> elements)
        {
            elements.forEach(element ->
            {
                ProcessorControlImpl processorControl = new ProcessorControlImpl(element, this::process);
                this.elementTypeToConsumer.forEach((elementType, elementConsumer) -> Optional.ofNullable(element)
                                                                                             .filter(iElement -> elementType.isAssignableFrom(iElement.getClass()))
                                                                                             .map(MapperUtils.identityCast(elementType))
                                                                                             .ifPresent(iElement -> elementConsumer.accept(iElement,
                                                                                                                                           processorControl)));
                processorControl.processChildrenNowIfProcessingStillAllowed();
            });
        }
    }

    public static interface Element
    {
        public default Optional<Text> asText()
        {
            return as(Text.class);
        }

        public default Optional<Code> asCode()
        {
            return as(Code.class);
        }

        public default Optional<CodeBlock> asCodeBlock()
        {
            return as(CodeBlock.class);
        }

        public default Optional<Html> asHtml()
        {
            return as(Html.class);
        }

        public default Optional<HtmlBlock> asHtmlBlock()
        {
            return as(HtmlBlock.class);
        }

        public default Optional<ThematicBreak> asThematicBreak()
        {
            return as(ThematicBreak.class);
        }

        public default Optional<TaskListMarker> asTaskListMarker()
        {
            return as(TaskListMarker.class);
        }

        public default Optional<CustomIdentifier> asCustomIdentifier()
        {
            return as(CustomIdentifier.class);
        }

        public default Optional<Heading> asHeading()
        {
            return as(Heading.class);
        }

        public default Optional<LineBreak> asLineBreak()
        {
            return as(LineBreak.class);
        }

        public default Optional<Link> asLink()
        {
            return as(Link.class);
        }

        public default Optional<Image> asImage()
        {
            return as(Image.class);
        }

        public default Optional<Paragraph> asParagraph()
        {
            return as(Paragraph.class);
        }

        public default Optional<UnorderedList> asUnorderedList()
        {
            return as(UnorderedList.class);
        }

        public default Optional<OrderedList> asOrderedList()
        {
            return as(OrderedList.class);
        }

        public default Optional<Table> asTable()
        {
            return as(Table.class);
        }

        @SuppressWarnings("unchecked")
        public default <T extends Element> Optional<T> as(Class<T> type)
        {
            return Optional.of(this)
                           .filter(element -> type.isAssignableFrom(element.getClass()))
                           .map(element -> (T) element);
        }

        public default Optional<ElementWithChildren> asElementWithChildren()
        {
            return as(ElementWithChildren.class);
        }

        /**
         * Returns the line number, starting at 1, of the markdown source this {@link Element} was parsed from. Returns {@link Optional#empty()} for
         * {@link Element} types that do not track their origin.
         *
         * @return
         */
        public default Optional<Integer> getSourceLine()
        {
            return Optional.empty();
        }

        public Optional<? extends Element> cloneAndFilter(Predicate<Element> inclusionFilter);
    }

    public static interface ElementWithChildren extends Element
    {

        /**
         * Returns all child {@link Element}s of the current {@link Element}. Returns an empty {@link List}, if no children are present.
         * 
         * @return
         */
        public default List<Element> getChildren()
        {
            return Collections.emptyList();
        }
    }

    public static class Table implements ElementWithChildren
    {
        private List<Column> columns;
        private List<Row>    rows;

        public Table(List<Row> rows, List<Column> columns)
        {
            super();
            this.rows = rows;
            this.columns = columns;
        }

        public static class Row implements ElementWithChildren
        {
            private List<Cell> cells;

            public Row(List<Cell> cells)
            {
                super();
                this.cells = cells;
            }

            public List<Cell> getCells()
            {
                return this.cells;
            }

            @Override
            public String toString()
            {
                return "Row [cells=" + this.cells + "]";
            }

            @Override
            public List<Element> getChildren()
            {
                return this.getCells()
                           .stream()
                           .collect(Collectors.toList());
            }

            @Override
            public Optional<Row> cloneAndFilter(Predicate<Element> inclusionFilter)
            {
                return Optional.of(new Row(cloneAndFilterElements(this.cells, inclusionFilter)))
                               .filter(inclusionFilter);
            }
        }

        public static class Cell extends Column
        {
            public Cell(List<Element> elements)
            {
                super(elements);
            }

            public Cell(List<Element> elements, Alignment alignment)
            {
                super(elements, alignment);
            }

            @Override
            public String toString()
            {
                return "Cell [getElements()=" + this.getElements() + "]";
            }

            @Override
            public Optional<Cell> cloneAndFilter(Predicate<Element> inclusionFilter)
            {
                return Optional.of(new Cell(cloneAndFilterElements(this.getElements(), inclusionFilter), this.getAlignment()
                                                                                                            .orElse(null)))
                               .filter(inclusionFilter);
            }
        }

        /**
         * Horizontal alignment of a table {@link Column}, declared by the colons of the delimiter row like <code>|:--|:-:|--:|</code>.
         *
         * @author omnaest
         */
        public static enum Alignment
        {
            LEFT, CENTER, RIGHT
        }

        public static class Column implements ElementWithChildren
        {
            private List<Element> elements;
            private Alignment     alignment;

            public Column(List<Element> elements)
            {
                this(elements, null);
            }

            public Column(List<Element> elements, Alignment alignment)
            {
                super();
                this.elements = elements;
                this.alignment = alignment;
            }

            /**
             * Returns the horizontal alignment declared for this column. Returns {@link Optional#empty()} if the delimiter row does not declare one.
             *
             * @return
             */
            public Optional<Alignment> getAlignment()
            {
                return Optional.ofNullable(this.alignment);
            }

            public List<Element> getElements()
            {
                return this.elements;
            }

            public String toText()
            {
                return this.elements.stream()
                                    .map(element -> element.asText()
                                                           .map(Text::getValue)
                                                           .orElse(""))
                                    .collect(Collectors.joining());
            }

            @Override
            public String toString()
            {
                return "Column [alignment=" + this.alignment + ", elements=" + this.elements + "]";
            }

            @Override
            public List<Element> getChildren()
            {
                return this.getElements();
            }

            @Override
            public Optional<? extends Column> cloneAndFilter(Predicate<Element> inclusionFilter)
            {
                return Optional.of(new Column(cloneAndFilterElements(this.elements, inclusionFilter), this.alignment))
                               .filter(inclusionFilter);
            }

        }

        public List<Column> getColumns()
        {
            return this.columns;
        }

        public List<Row> getRows()
        {
            return this.rows;
        }

        @Override
        public String toString()
        {
            return "Table [columns=" + this.columns + ", rows=" + this.rows + "]";
        }

        public org.omnaest.utils.table.Table asStringTable()
        {
            org.omnaest.utils.table.Table table = org.omnaest.utils.table.Table.newInstance();
            table.addColumnTitles(this.getColumns()
                                      .stream()
                                      .map(Column::toText)
                                      .collect(Collectors.toList()));
            this.getRows()
                .forEach(row -> table.addRow(row.getCells()
                                                .stream()
                                                .map(Column::toText)
                                                .collect(Collectors.toList())));
            return table;
        }

        @Override
        public List<Element> getChildren()
        {
            return Stream.concat(this.getColumns()
                                     .stream(),
                                 this.getRows()
                                     .stream())
                         .collect(Collectors.toList());
        }

        public Stream<String> getCustomIds()
        {
            return StreamUtils.recursiveFlattened(this.getChildren()
                                                      .stream(),
                                                  element ->
                                                  {
                                                      if (element instanceof ElementWithChildren)
                                                      {
                                                          return ((ElementWithChildren) element).getChildren()
                                                                                                .stream();
                                                      }
                                                      else
                                                      {
                                                          return Stream.empty();
                                                      }
                                                  })
                              .map(Element::asCustomIdentifier)
                              .filter(Optional::isPresent)
                              .map(Optional::get)
                              .map(CustomIdentifier::getIdentifier);
        }

        @Override
        public Optional<Table> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Table(cloneAndFilterElements(this.rows, inclusionFilter),
                                                 cloneAndFilterElements(this.columns, inclusionFilter)))
                           .filter(inclusionFilter);
        }

    }

    public static class LineBreak implements Element
    {
        private final boolean hard;

        public LineBreak()
        {
            this(false);
        }

        /**
         * @param hard
         *            true for an explicit line break (a line ending with two spaces or a backslash), false for a soft line break (a simple newline within a
         *            paragraph)
         */
        public LineBreak(boolean hard)
        {
            super();
            this.hard = hard;
        }

        /**
         * Returns true if the author requested this break explicitly, e.g. by ending the line with two spaces or a backslash.
         *
         * @return
         */
        public boolean isHard()
        {
            return this.hard;
        }

        @Override
        public String toString()
        {
            return "LineBreak [hard=" + this.hard + "]";
        }

        @Override
        public Optional<LineBreak> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new LineBreak(this.hard))
                           .filter(inclusionFilter);
        }
    }

    public static class Text implements Element
    {
        private String  value;
        private boolean bold;
        private boolean italic;
        private boolean strikethrough;
        private Integer sourceLine;

        public Text(String value, boolean bold)
        {
            this(value, bold, false);
        }

        public Text(String value, boolean bold, boolean italic)
        {
            this(value, bold, italic, null);
        }

        public Text(String value, boolean bold, boolean italic, Integer sourceLine)
        {
            this(value, bold, italic, false, sourceLine);
        }

        public Text(String value, boolean bold, boolean italic, boolean strikethrough, Integer sourceLine)
        {
            super();
            this.value = value;
            this.bold = bold;
            this.italic = italic;
            this.strikethrough = strikethrough;
            this.sourceLine = sourceLine;
        }

        /**
         * Returns true if this {@link Text} is wrapped into the double tilde markup like {@code ~~struck~~}, which is a github flavored markdown extension.
         *
         * @see #isBold()
         * @see #isItalic()
         * @return
         */
        public boolean isStrikethrough()
        {
            return this.strikethrough;
        }

        @Override
        public Optional<Integer> getSourceLine()
        {
            return Optional.ofNullable(this.sourceLine);
        }

        public String getValue()
        {
            return this.value;
        }

        /**
         * Returns true if this {@link Text} is wrapped into a strong emphasis, which is the double asterisk or underscore markup like {@code **bold**}.
         *
         * @see #isItalic()
         * @return
         */
        public boolean isBold()
        {
            return this.bold;
        }

        /**
         * Returns true if this {@link Text} is wrapped into an emphasis, which is the single asterisk or underscore markup like {@code *italic*}.
         *
         * @see #isBold()
         * @return
         */
        public boolean isItalic()
        {
            return this.italic;
        }

        @Override
        public String toString()
        {
            return "Text [value=" + this.value + ", bold=" + this.bold + ", italic=" + this.italic + ", strikethrough=" + this.strikethrough + "]";
        }

        @Override
        public Optional<Text> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Text(this.value, this.bold, this.italic, this.strikethrough, this.sourceLine))
                           .filter(inclusionFilter);
        }

    }

    /**
     * Inline code, which is the single backtick markup like {@code `code`}.
     *
     * @see CodeBlock
     * @author omnaest
     */
    public static class Code implements Element
    {
        private final String value;

        public Code(String value)
        {
            super();
            this.value = value;
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "Code [value=" + this.value + "]";
        }

        @Override
        public Optional<Code> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Code(this.value))
                           .filter(inclusionFilter);
        }
    }

    /**
     * A block of code, either fenced by triple backticks or indented by four spaces. A fenced block can declare a {@link #getLanguage()}.
     *
     * @see Code
     * @author omnaest
     */
    public static class CodeBlock implements Element
    {
        private final String value;
        private final String language;

        public CodeBlock(String value, String language)
        {
            super();
            this.value = value;
            this.language = language;
        }

        public String getValue()
        {
            return this.value;
        }

        /**
         * Returns the info string of a fenced code block, which is the language token like the {@code java} in {@code ```java}. Returns
         * {@link Optional#empty()} for an indented code block or a fenced block without info string.
         *
         * @return
         */
        public Optional<String> getLanguage()
        {
            return Optional.ofNullable(this.language)
                           .filter(StringUtils::isNotBlank);
        }

        @Override
        public String toString()
        {
            return "CodeBlock [language=" + this.language + ", value=" + this.value + "]";
        }

        @Override
        public Optional<CodeBlock> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new CodeBlock(this.value, this.language))
                           .filter(inclusionFilter);
        }
    }

    /**
     * The checkbox of a task list item like <code>- [x] done</code>, which is a github flavored markdown extension. The marker is provided as the first
     * {@link Element} of the item, the text of the item follows it.
     *
     * @author omnaest
     */
    public static class TaskListMarker implements Element
    {
        private final boolean checked;

        public TaskListMarker(boolean checked)
        {
            super();
            this.checked = checked;
        }

        public boolean isChecked()
        {
            return this.checked;
        }

        @Override
        public String toString()
        {
            return "TaskListMarker [checked=" + this.checked + "]";
        }

        @Override
        public Optional<TaskListMarker> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new TaskListMarker(this.checked))
                           .filter(inclusionFilter);
        }
    }

    /**
     * Raw inline html like the <code>&lt;b&gt;</code> of <code>text &lt;b&gt;bold&lt;/b&gt; text</code>. The text around and within the tags is provided as
     * regular {@link Text}, this element carries the tag itself.
     *
     * @see HtmlBlock
     * @author omnaest
     */
    public static class Html implements Element
    {
        private final String value;

        public Html(String value)
        {
            super();
            this.value = value;
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "Html [value=" + this.value + "]";
        }

        @Override
        public Optional<Html> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Html(this.value))
                           .filter(inclusionFilter);
        }
    }

    /**
     * A block of raw html, which is a block level element starting with an html tag. Its content is not interpreted as markdown at all.
     *
     * @see Html
     * @author omnaest
     */
    public static class HtmlBlock implements Element
    {
        private final String value;

        public HtmlBlock(String value)
        {
            super();
            this.value = value;
        }

        public String getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "HtmlBlock [value=" + this.value + "]";
        }

        @Override
        public Optional<HtmlBlock> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new HtmlBlock(this.value))
                           .filter(inclusionFilter);
        }
    }

    /**
     * A horizontal separator line, written as three or more asterisks, dashes or underscores on a line of their own.
     *
     * @author omnaest
     */
    public static class ThematicBreak implements Element
    {
        @Override
        public String toString()
        {
            return "ThematicBreak []";
        }

        @Override
        public Optional<ThematicBreak> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new ThematicBreak())
                           .filter(inclusionFilter);
        }
    }

    public static class CustomIdentifier implements Element
    {
        private String identifier;

        public CustomIdentifier(String identifier)
        {
            super();
            this.identifier = identifier;
        }

        public String getIdentifier()
        {
            return this.identifier;
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("CustomIdentifier [identifier=")
                   .append(this.identifier)
                   .append("]");
            return builder.toString();
        }

        @Override
        public Optional<CustomIdentifier> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new CustomIdentifier(this.identifier))
                           .filter(inclusionFilter);
        }
    }

    public static class BasicList implements ElementWithChildren
    {
        private List<Element> elements;
        private boolean       tight;

        public BasicList(List<Element> elements)
        {
            this(elements, true);
        }

        public BasicList(List<Element> elements, boolean tight)
        {
            super();
            this.elements = elements;
            this.tight = tight;
        }

        /**
         * Returns true for a list whose items are not separated by blank lines. A loose list, which is the opposite, is usually rendered with more spacing
         * between its items.
         *
         * @return
         */
        public boolean isTight()
        {
            return this.tight;
        }

        public List<Element> getElements()
        {
            return this.elements;
        }

        @Override
        public List<Element> getChildren()
        {
            return this.getElements();
        }

        @Override
        public String toString()
        {
            return this.getClass()
                       .getSimpleName()
                + " [tight=" + this.tight + ", elements=" + this.elements + "]";
        }

        @Override
        public Optional<? extends BasicList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new BasicList(cloneAndFilterElements(this.elements, inclusionFilter), this.tight))
                           .filter(inclusionFilter);
        }
    }

    public static class UnorderedList extends BasicList
    {
        public UnorderedList(List<Element> elements)
        {
            super(elements);
        }

        public UnorderedList(List<Element> elements, boolean tight)
        {
            super(elements, tight);
        }

        @Override
        public Optional<UnorderedList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new UnorderedList(cloneAndFilterElements(this.getElements(), inclusionFilter), this.isTight()))
                           .filter(inclusionFilter);
        }
    }

    public static class OrderedList extends BasicList
    {
        private int startNumber;

        public OrderedList(List<Element> elements)
        {
            this(elements, 1, true);
        }

        public OrderedList(List<Element> elements, int startNumber, boolean tight)
        {
            super(elements, tight);
            this.startNumber = startNumber;
        }

        /**
         * Returns the number the list starts counting at, which is 1 for a regular list and e.g. 5 for a list written as <code>5. first item</code>.
         *
         * @return
         */
        public int getStartNumber()
        {
            return this.startNumber;
        }

        @Override
        public String toString()
        {
            return "OrderedList [startNumber=" + this.startNumber + ", tight=" + this.isTight() + ", elements=" + this.getElements() + "]";
        }

        @Override
        public Optional<OrderedList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new OrderedList(cloneAndFilterElements(this.getElements(), inclusionFilter), this.startNumber, this.isTight()))
                           .filter(inclusionFilter);
        }
    }

    public static class Heading implements Element
    {
        private int           level;
        private List<Element> elements;

        public Heading(int level, List<Element> elements)
        {
            super();
            this.level = level;
            this.elements = elements;
        }

        public int getStrength()
        {
            return this.level;
        }

        public List<String> getCustomIds()
        {
            return this.getElements()
                       .stream()
                       .map(Element::asCustomIdentifier)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .map(CustomIdentifier::getIdentifier)
                       .collect(Collectors.toList());
        }

        public String getText()
        {
            return this.getElements()
                       .stream()
                       .flatMap(element ->
                       {
                           if (element.asText()
                                      .isPresent())
                           {
                               return Stream.of(element.asText()
                                                       .get()
                                                       .getValue());
                           }
                           else if (element.asLink()
                                           .isPresent())
                           {
                               return Stream.of(element.asLink()
                                                       .get()
                                                       .getLabel());
                           }
                           else if (element.asImage()
                                           .isPresent())
                           {
                               return Stream.of(element.asImage()
                                                       .get()
                                                       .getLabel());
                           }
                           else
                           {
                               return Stream.empty();
                           }
                       })
                       .filter(PredicateUtils.notNull())
                       .collect(Collectors.joining());
        }

        public List<Element> getElements()
        {
            return this.elements;
        }

        public List<Link> getLinks()
        {
            return this.getElements()
                       .stream()
                       .map(Element::asLink)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .collect(Collectors.toList());
        }

        public List<Image> getImages()
        {
            return this.getElements()
                       .stream()
                       .map(Element::asImage)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .collect(Collectors.toList());
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("Heading [level=")
                   .append(this.level)
                   .append(", elements=")
                   .append(this.elements)
                   .append("]");
            return builder.toString();
        }

        @Override
        public Optional<Heading> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Heading(this.level, cloneAndFilterElements(this.getElements(), inclusionFilter)))
                           .filter(inclusionFilter);
        }

    }

    public static class Link implements Element
    {
        private String        link;
        private String        tooltip;
        private List<Element> elements;
        private Integer       sourceLine;

        public Link(String link, List<Element> elements, String tooltip)
        {
            this(link, elements, tooltip, null);
        }

        public Link(String link, List<Element> elements, String tooltip, Integer sourceLine)
        {
            super();
            this.link = link;
            this.elements = elements;
            this.tooltip = tooltip;
            this.sourceLine = sourceLine;
        }

        @Override
        public Optional<Integer> getSourceLine()
        {
            return Optional.ofNullable(this.sourceLine);
        }

        public String getLink()
        {
            return this.link;
        }

        public String getLabel()
        {
            return this.getElements()
                       .stream()
                       .map(element -> element.asText()
                                              .map(Text::getValue)
                                              .orElseGet(() -> element.asCode()
                                                                      .map(Code::getValue)
                                                                      .orElse("")))
                       .collect(Collectors.joining());
        }

        /**
         * Returns the {@link Element}s the label of this {@link Link} is composed of, which allows to access markup the flattened {@link #getLabel()} cannot
         * express, like a nested {@link Image}.
         *
         * @return
         */
        public List<Element> getElements()
        {
            return Optional.ofNullable(this.elements)
                           .orElse(Collections.emptyList());
        }

        public List<String> getCustomIds()
        {
            return this.getElements()
                       .stream()
                       .map(Element::asCustomIdentifier)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .map(CustomIdentifier::getIdentifier)
                       .collect(Collectors.toList());
        }

        public String getTooltip()
        {
            return this.tooltip;
        }

        @Override
        public String toString()
        {
            return "Link [link=" + this.link + ", label=" + this.getLabel() + ", tooltip=" + this.tooltip + "]";
        }

        @Override
        public Optional<Link> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Link(this.link, cloneAndFilterElements(this.elements, inclusionFilter), this.tooltip, this.sourceLine))
                           .filter(inclusionFilter);
        }
    }

    public static class Paragraph implements ElementWithChildren
    {
        private List<Element> elements;

        public Paragraph(List<Element> elements)
        {
            super();
            this.elements = elements;
        }

        public List<Element> getElements()
        {
            return this.elements;
        }

        @Override
        public String toString()
        {
            return "Paragraph [elements=" + this.elements + "]";
        }

        @Override
        public List<Element> getChildren()
        {
            return this.getElements();
        }

        @Override
        public Optional<Paragraph> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Paragraph(cloneAndFilterElements(this.elements, inclusionFilter)))
                           .filter(inclusionFilter);
        }
    }

    public static class Image implements Element
    {
        private String link;
        private String label;
        private String tooltip;

        public Image(String link, String label, String tooltip)
        {
            super();
            this.link = link;
            this.label = label;
            this.tooltip = tooltip;
        }

        public String getLink()
        {
            return this.link;
        }

        public String getTooltip()
        {
            return this.tooltip;
        }

        public String getLabel()
        {
            return this.label;
        }

        @Override
        public String toString()
        {
            return "Image [link=" + this.link + ", label=" + this.label + ", tooltip=" + this.tooltip + "]";
        }

        @Override
        public Optional<Image> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.of(new Image(this.link, this.label, this.tooltip))
                           .filter(inclusionFilter);
        }
    }

    public static interface MarkdownParsedDocument
    {
        public Stream<Element> get();

        public <E extends Element> Optional<E> findFirst(Class<E> elementType);

        public MarkdownProcessor newProcessor();

        public <E extends Element> Stream<E> getAndFilter(Class<E> elementType);

        public MarkdownParsedDocument clearCustomTokens();
    }

    public static interface MarkdownSubProcessor
    {
        public <E extends Element> MarkdownSubProcessor addVisitor(Class<E> elementType, Consumer<E> elementConsumer);

        public <E extends Element> MarkdownSubProcessor addVisitor(Class<E> elementType, BiConsumer<E, MarkdownProcessorControl> elementConsumer);

    }

    public static interface MarkdownProcessor extends MarkdownSubProcessor
    {
        public MarkdownParsedDocument process();

        @Override
        public <E extends Element> MarkdownProcessor addVisitor(Class<E> elementType, Consumer<E> elementConsumer);

        @Override
        public <E extends Element> MarkdownProcessor addVisitor(Class<E> elementType, BiConsumer<E, MarkdownProcessorControl> elementConsumer);

    }

    public static interface MarkdownProcessorControl
    {
        public MarkdownProcessorControl processChildrenNow();

        public MarkdownProcessorControl processChildrenNowWith(Consumer<MarkdownSubProcessor> subProcessorConsumer);

        public MarkdownProcessorControl doNotProcessChildren();
    }

    public static class MarkdownParseOptions
    {
        private boolean wrapIntoParagraphs  = false;
        private boolean parseCustomIdTokens = false;

        protected MarkdownParseOptions()
        {
            super();
        }

        public MarkdownParseOptions enableWrapIntoParagraphs()
        {
            return this.enableWrapIntoParagraphs(true);
        }

        public MarkdownParseOptions enableWrapIntoParagraphs(boolean wrapIntoParagraphs)
        {
            this.wrapIntoParagraphs = wrapIntoParagraphs;
            return this;
        }

        public boolean isWrapIntoParagraphs()
        {
            return this.wrapIntoParagraphs;
        }

        public boolean isParseCustomIdTokens()
        {
            return this.parseCustomIdTokens;
        }

        public MarkdownParseOptions enableParseCustomIdTokens()
        {
            return this.enableParseCustomIdTokens(true);
        }

        public MarkdownParseOptions enableParseCustomIdTokens(boolean parseCustomIdTokens)
        {
            this.parseCustomIdTokens = parseCustomIdTokens;
            return this;
        }
    }

    public static MarkdownParsedDocument parse(String text)
    {
        return parse(text, ConsumerUtils.noOperation());
    }

    public static MarkdownParsedDocument parse(String text, Consumer<MarkdownParseOptions> optionsConsumer)
    {
        //
        MarkdownParseOptions options = new MarkdownParseOptions();
        Optional.ofNullable(optionsConsumer)
                .ifPresent(consumer -> consumer.accept(options));
        return parse(text, options);
    }

    private static MarkdownParsedDocument parse(String text, MarkdownParseOptions options)
    {
        //
        List<Extension> extensions = Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
                                                   AutolinkExtension.create());
        Parser parser = Parser.builder()
                              .extensions(extensions)
                              .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                              .build();
        Node document = parser.parse(text);

        List<Element> elements = new ArrayList<>();

        //
        Consumer<Element> elementConsumer = e -> elements.add(e);
        document.accept(new ElementConsumerDrivenVisitor(elementConsumer, options));

        //
        return new MarkdownParsedDocumentImpl(elements);
    }

    private static class ProcessorControlImpl implements MarkdownProcessorControl
    {
        private final Element                 element;
        private final Consumer<List<Element>> elementsProcessor;

        private boolean                       childrenProcessing = true;

        private ProcessorControlImpl(Element element, Consumer<List<Element>> elementsProcessor)
        {
            this.element = element;
            this.elementsProcessor = elementsProcessor;
        }

        @Override
        public MarkdownProcessorControl processChildrenNow()
        {
            this.processChildrenNowIfProcessingStillAllowed();
            return this;
        }

        @Override
        public MarkdownProcessorControl processChildrenNowWith(Consumer<MarkdownSubProcessor> interpreterConsumer)
        {
            MarkdownSubProcessorImpl subProcessor = new MarkdownSubProcessorImpl();
            interpreterConsumer.accept(subProcessor);
            subProcessor.process(this.resolveChildren());
            this.childrenProcessing = false;
            return this;
        }

        @Override
        public MarkdownProcessorControl doNotProcessChildren()
        {
            this.childrenProcessing = false;
            return this;
        }

        public ProcessorControlImpl processChildrenNowIfProcessingStillAllowed()
        {
            if (this.childrenProcessing)
            {
                this.elementsProcessor.accept(this.resolveChildren());
                this.childrenProcessing = false;
            }
            return this;
        }

        private List<Element> resolveChildren()
        {
            return this.element.asElementWithChildren()
                               .map(ElementWithChildren::getChildren)
                               .orElse(Collections.emptyList());
        }

    }

    /**
     * Maps the alignment of a commonmark table cell onto the {@link Table.Alignment} of this api. Returns null if no alignment is declared.
     *
     * @param alignment
     * @return
     */
    private static Table.Alignment determineAlignment(TableCell.Alignment alignment)
    {
        return EnumUtils.mapByName(alignment, Table.Alignment.class)
                        .orElse(null);
    }

    /**
     * Determines the line number, starting at 1, of the given commonmark {@link Node}. Returns null if the parser did not attach any source span to it.
     *
     * @param node
     * @return
     */
    private static Integer determineSourceLine(Node node)
    {
        return Optional.ofNullable(node)
                       .map(Node::getSourceSpans)
                       .filter(PredicateUtils.listNotEmpty())
                       .map(sourceSpans -> sourceSpans.get(0))
                       .map(sourceSpan -> sourceSpan.getLineIndex() + 1)
                       .orElse(null);
    }

    private static class ElementConsumerDrivenVisitor extends AbstractVisitor
    {
        private final Consumer<Element> elementConsumer;
        private final boolean           inheritedBold;
        private final boolean           inheritedItalic;
        private final boolean           inheritedStrikethrough;
        private boolean                 bold          = false;
        private boolean                 italic        = false;
        private boolean                 strikethrough = false;
        private MarkdownParseOptions    options;

        private ElementConsumerDrivenVisitor(Consumer<Element> elementConsumer, MarkdownParseOptions options)
        {
            this(elementConsumer, options, false, false, false);
        }

        /**
         * A nested {@link Element} like a {@link Heading} or {@link Link} is visited by an own {@link ElementConsumerDrivenVisitor}, so any surrounding emphasis
         * has to be handed over explicitly to not get lost on the way down.
         */
        private ElementConsumerDrivenVisitor(Consumer<Element> elementConsumer, MarkdownParseOptions options, boolean inheritedBold, boolean inheritedItalic,
                                            boolean inheritedStrikethrough)
        {
            this.elementConsumer = elementConsumer;
            this.options = options;
            this.inheritedBold = inheritedBold;
            this.inheritedItalic = inheritedItalic;
            this.inheritedStrikethrough = inheritedStrikethrough;
        }

        private ElementConsumerDrivenVisitor newChildVisitor(Consumer<Element> elementConsumer)
        {
            return new ElementConsumerDrivenVisitor(elementConsumer, this.options, this.isBold(), this.isItalic(), this.isStrikethrough());
        }

        private boolean isBold()
        {
            return this.bold || this.inheritedBold;
        }

        private boolean isItalic()
        {
            return this.italic || this.inheritedItalic;
        }

        private boolean isStrikethrough()
        {
            return this.strikethrough || this.inheritedStrikethrough;
        }

        /**
         * Single asterisk or underscore markup like {@code *italic*}.
         *
         * @see #visit(StrongEmphasis)
         */
        @Override
        public void visit(Emphasis emphasis)
        {
            boolean previousItalic = this.italic;
            this.italic = true;

            super.visit(emphasis);

            this.italic = previousItalic;
        }

        /**
         * Double asterisk or underscore markup like {@code **bold**}, which is a node type of its own and not an {@link Emphasis} with another delimiter.
         *
         * @see #visit(Emphasis)
         */
        @Override
        public void visit(StrongEmphasis strongEmphasis)
        {
            boolean previousBold = this.bold;
            this.bold = true;

            super.visit(strongEmphasis);

            this.bold = previousBold;
        }

        @Override
        public void visit(SoftLineBreak softLineBreak)
        {
            this.elementConsumer.accept(new LineBreak(false));
            super.visit(softLineBreak);
        }

        @Override
        public void visit(HardLineBreak hardLineBreak)
        {
            this.elementConsumer.accept(new LineBreak(true));
            super.visit(hardLineBreak);
        }

        @Override
        public void visit(org.commonmark.node.Code code)
        {
            this.elementConsumer.accept(new Code(code.getLiteral()));
            super.visit(code);
        }

        @Override
        public void visit(org.commonmark.node.HtmlBlock htmlBlock)
        {
            this.elementConsumer.accept(new HtmlBlock(htmlBlock.getLiteral()));
            super.visit(htmlBlock);
        }

        @Override
        public void visit(HtmlInline htmlInline)
        {
            this.elementConsumer.accept(new Html(htmlInline.getLiteral()));
            super.visit(htmlInline);
        }

        @Override
        public void visit(org.commonmark.node.ThematicBreak thematicBreak)
        {
            this.elementConsumer.accept(new ThematicBreak());
            super.visit(thematicBreak);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock)
        {
            this.elementConsumer.accept(new CodeBlock(fencedCodeBlock.getLiteral(), fencedCodeBlock.getInfo()));
            super.visit(fencedCodeBlock);
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock)
        {
            this.elementConsumer.accept(new CodeBlock(indentedCodeBlock.getLiteral(), null));
            super.visit(indentedCodeBlock);
        }

        @Override
        public void visit(org.commonmark.node.Text text)
        {
            String value = text.getLiteral();
            String nonInterpretableValue = this.options.isParseCustomIdTokens() ? MatcherUtils.interpreter()
                                                                                              .ifContainsRegEx(CUSTOM_ID_TOKEN_REGEX,
                                                                                                               customIdMatch -> customIdMatch.getSubGroupsAsStream()
                                                                                                                                             .forEach(group -> this.elementConsumer.accept(new CustomIdentifier(group))))
                                                                                              .apply(value)
                    : value;
            this.elementConsumer.accept(new Text(nonInterpretableValue, this.isBold(), this.isItalic(), this.isStrikethrough(), determineSourceLine(text)));
            super.visit(text);
        }

        @Override
        public void visit(org.commonmark.node.Heading heading)
        {
            int level = heading.getLevel();
            List<Element> elements = new ArrayList<>();
            this.newChildVisitor(elements::add).visitChildren(heading);
            this.elementConsumer.accept(new Heading(level, elements));
        }

        @Override
        public void visit(org.commonmark.node.Link link)
        {
            List<Element> elements = new ArrayList<>();
            this.newChildVisitor(elements::add).visitChildren(link);
            this.elementConsumer.accept(new Link(link.getDestination(), elements, link.getTitle(), determineSourceLine(link)));
        }

        @Override
        public void visit(org.commonmark.node.Image image)
        {
            AtomicReference<String> label = new AtomicReference<>();
            image.accept(new AbstractVisitor() {
                @Override
                public void visit(org.commonmark.node.Text text)
                {
                    label.updateAndGet(previous -> Optional.ofNullable(previous)
                                                           .orElse("")
                                                   + text.getLiteral());
                }
            });
            this.elementConsumer.accept(new Image(image.getDestination(), label.get(), image.getTitle()));
        }

        @Override
        public void visit(org.commonmark.node.Paragraph paragraph)
        {
            if (this.options.isWrapIntoParagraphs())
            {
                List<Element> elements = new ArrayList<>();
                this.newChildVisitor(elements::add).visitChildren(paragraph);
                this.elementConsumer.accept(new Paragraph(elements));
            }
            else
            {
                super.visit(paragraph);
            }
        }

        @Override
        public void visit(BulletList bulletList)
        {
            List<Element> elements = new ArrayList<>();
            this.newChildVisitor(elements::add).visitChildren(bulletList);
            this.elementConsumer.accept(new UnorderedList(elements, bulletList.isTight()));
        }

        @Override
        public void visit(org.commonmark.node.OrderedList orderedList)
        {
            List<Element> elements = new ArrayList<>();
            this.newChildVisitor(elements::add).visitChildren(orderedList);
            this.elementConsumer.accept(new OrderedList(elements, orderedList.getStartNumber(), orderedList.isTight()));
        }

        @Override
        public void visit(CustomBlock customBlock)
        {
            if (customBlock instanceof TableBlock)
            {
                List<Element> elements = this.parseChildrenElements(customBlock);
                List<Row> rows = elements.stream()
                                         .filter(element -> element instanceof Table.Row)
                                         .map(element -> (Table.Row) element)
                                         .collect(Collectors.toList());
                List<Column> columns = elements.stream()
                                               .filter(element -> element instanceof Table.Column)
                                               .map(element -> (Table.Column) element)
                                               .collect(Collectors.toList());
                this.elementConsumer.accept(new Table(rows, columns));
            }
            else
            {
                super.visit(customBlock);
            }
        }

        @Override
        public void visit(CustomNode customNode)
        {
            if (customNode instanceof TableHead)
            {
                this.parseChildrenElements(customNode)
                    .stream()
                    .filter(element -> element instanceof Table.Row)
                    .map(element -> (Table.Row) element)
                    .flatMap(row -> row.getCells()
                                       .stream())
                    .forEach(this.elementConsumer::accept);
            }
            else if (customNode instanceof TableRow)
            {
                this.elementConsumer.accept(new Table.Row(this.parseChildrenElements(customNode)
                                                              .stream()
                                                              .filter(element -> element instanceof Table.Cell)
                                                              .map(element -> (Table.Cell) element)
                                                              .collect(Collectors.toList())));
            }
            else if (customNode instanceof TableCell)
            {
                this.elementConsumer.accept(new Table.Cell(this.parseChildrenElements(customNode),
                                                           determineAlignment(((TableCell) customNode).getAlignment())));
            }
            else if (customNode instanceof TableBody)
            {
                this.parseChildrenElements(customNode)
                    .forEach(this.elementConsumer::accept);
            }
            else if (customNode instanceof Strikethrough)
            {
                boolean previousStrikethrough = this.strikethrough;
                this.strikethrough = true;

                super.visit(customNode);

                this.strikethrough = previousStrikethrough;
            }
            else if (customNode instanceof TaskListItemMarker)
            {
                this.elementConsumer.accept(new TaskListMarker(((TaskListItemMarker) customNode).isChecked()));
            }
            else
            {
                super.visit(customNode);
            }
        }

        private List<Element> parseChildrenElements(Node node)
        {
            List<Element> elements = new ArrayList<>();
            this.newChildVisitor(elements::add).visitChildren(node);
            return elements;
        }

    }

    public static interface MarkdownTextBuilder<B>
    {
        public B addText(String text);

        public B addTexts(String... texts);

        public <E> B process(Stream<E> elements, BiConsumer<E, B> elementAndBuilderConsumer);

        public <E> B process(Collection<E> elements, BiConsumer<E, B> elementAndBuilderConsumer);
    }

    public static interface MarkdownDocumentBuilder extends MarkdownTextBuilder<MarkdownDocumentBuilder>
    {
        public MarkdownDocument build();

        public MarkdownDocumentBuilder addHeading(String header);

        public MarkdownDocumentBuilder addHeading(HeadingStrength headingStrength, String heading);

        public MarkdownDocumentBuilder addLink(String label, String link, String tooltip);

        /**
         * Adds an image, e.g. <code>![label](link)</code>.
         *
         * @param label
         * @param link
         * @return
         */
        public MarkdownDocumentBuilder addImage(String label, String link);

        /**
         * Adds bold text, e.g. <code>**text**</code>.
         *
         * @see #addItalicText(String)
         * @param text
         * @return
         */
        public MarkdownDocumentBuilder addBoldText(String text);

        /**
         * Adds italic text, e.g. <code>*text*</code>.
         *
         * @see #addBoldText(String)
         * @param text
         * @return
         */
        public MarkdownDocumentBuilder addItalicText(String text);

        /**
         * Adds struck through text, e.g. <code>~~text~~</code>, which is a github flavored markdown extension.
         *
         * @param text
         * @return
         */
        public MarkdownDocumentBuilder addStrikethroughText(String text);

        /**
         * Adds a task list with one item per given text, e.g. <code>- [x] done</code>, which is a github flavored markdown extension.
         *
         * @param textToChecked
         * @return
         */
        public MarkdownDocumentBuilder addTaskList(Map<String, Boolean> textToChecked);

        /**
         * Adds inline code, e.g. <code>`code`</code>.
         *
         * @see #addCodeBlock(String, String)
         * @param code
         * @return
         */
        public MarkdownDocumentBuilder addCode(String code);

        /**
         * Adds a fenced code block, e.g. <code>```java</code>. The language can be null.
         *
         * @see #addCode(String)
         * @param code
         * @param language
         * @return
         */
        public MarkdownDocumentBuilder addCodeBlock(String code, String language);

        /**
         * Adds an unordered list with one entry per given text.
         *
         * @see #addOrderedList(Collection)
         * @param texts
         * @return
         */
        public MarkdownDocumentBuilder addUnorderedList(Collection<String> texts);

        /**
         * Adds an ordered list with one entry per given text.
         *
         * @see #addUnorderedList(Collection)
         * @param texts
         * @return
         */
        public MarkdownDocumentBuilder addOrderedList(Collection<String> texts);

        /**
         * Adds a block quote with one quoted line per given text.
         *
         * @param texts
         * @return
         */
        public MarkdownDocumentBuilder addBlockQuote(Collection<String> texts);

        /**
         * Adds a horizontal separator line.
         *
         * @return
         */
        public MarkdownDocumentBuilder addThematicBreak();

        public MarkdownDocumentBuilder addParagraph(Consumer<MarkdownParagraphBuilder> paragraphBuilderConsumer);

        public MarkdownDocumentBuilder withLineBreakCharacter(String lineBreakCharacter);

        public MarkdownDocumentBuilder addTable(org.omnaest.utils.table.Table table);

        public MarkdownDocumentBuilder addLineBreak();

        /**
         * Applies the current {@link MarkdownDocumentBuilder} to the given {@link Consumer}
         * 
         * @see #applyToIf(boolean, Consumer)
         * @param builderConsumer
         * @return
         */
        public MarkdownDocumentBuilder applyTo(Consumer<MarkdownDocumentBuilder> builderConsumer);

        /**
         * If the given boolean condition is true, the {@link Consumer} is invoked and otherwise not
         * 
         * @see #applyTo(Consumer)
         * @param condition
         * @param builderConsumer
         * @return
         */
        public MarkdownDocumentBuilder applyToIf(boolean condition, Consumer<MarkdownDocumentBuilder> builderConsumer);

    }

    public static interface MarkdownParagraphBuilder extends MarkdownTextBuilder<MarkdownParagraphBuilder>
    {
    }

    public static enum HeadingStrength
    {
        H1, H2, H3, H4, H5, H6;

        public int getStrength()
        {
            return this.ordinal() + 1;
        }
    }

    public static interface MarkdownDocument extends Supplier<String>
    {
        public MarkdownParsedDocument parse();

        public MarkdownParsedDocument parse(Consumer<MarkdownParseOptions> optionsConsumer);

        public MarkdownDocument writeTo(File file);
    }

    public static MarkdownDocumentBuilder builder()
    {
        return new MarkdownDocumentBuilder() {
            private StringBuilder stringBuilder      = new StringBuilder();
            private String        lineBreakCharacter = "\n";

            @Override
            public MarkdownDocumentBuilder addHeading(String heading)
            {
                return this.addHeading(HeadingStrength.H1, heading);
            }

            @Override
            public MarkdownDocumentBuilder addHeading(HeadingStrength headingStrength, String heading)
            {
                this.appendRawLine(StringUtils.repeat("#", headingStrength.getStrength()) + " " + heading);
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addLink(String label, String link, String tooltip)
            {
                this.appendRawLine("[" + label + "](" + link + " \"" + tooltip + "\")");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addImage(String label, String link)
            {
                this.appendRawLine("![" + StringUtils.defaultString(label) + "](" + StringUtils.defaultString(link) + ")");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addBoldText(String text)
            {
                this.appendRawLine("**" + StringUtils.defaultString(text) + "**");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addItalicText(String text)
            {
                this.appendRawLine("*" + StringUtils.defaultString(text) + "*");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addStrikethroughText(String text)
            {
                this.appendRawLine("~~" + StringUtils.defaultString(text) + "~~");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addTaskList(Map<String, Boolean> textToChecked)
            {
                this.addRawLineBreak();
                Optional.ofNullable(textToChecked)
                        .orElse(Collections.emptyMap())
                        .forEach((text, checked) -> this.appendRawLine("- [" + (Boolean.TRUE.equals(checked) ? "x" : " ") + "] "
                                + StringUtils.defaultString(text)));
                this.addRawLineBreak();
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addCode(String code)
            {
                this.appendRawLine("`" + StringUtils.defaultString(code) + "`");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addCodeBlock(String code, String language)
            {
                this.addRawLineBreak();
                this.appendRawLine("```" + StringUtils.defaultString(language));
                this.appendRawLine(StringUtils.defaultString(code));
                this.appendRawLine("```");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addUnorderedList(Collection<String> texts)
            {
                return this.addList(texts, text -> "* " + StringUtils.defaultString(text));
            }

            @Override
            public MarkdownDocumentBuilder addOrderedList(Collection<String> texts)
            {
                AtomicInteger counter = new AtomicInteger(1);
                return this.addList(texts, text -> counter.getAndIncrement() + ". " + StringUtils.defaultString(text));
            }

            @Override
            public MarkdownDocumentBuilder addBlockQuote(Collection<String> texts)
            {
                return this.addList(texts, text -> "> " + StringUtils.defaultString(text));
            }

            @Override
            public MarkdownDocumentBuilder addThematicBreak()
            {
                this.addRawLineBreak();
                this.appendRawLine("***");
                return this;
            }

            /**
             * Writes a block of lines, each of them created by the given line mapper, surrounded by the blank lines a block level element needs.
             */
            private MarkdownDocumentBuilder addList(Collection<String> texts, Function<String, String> lineMapper)
            {
                this.addRawLineBreak();
                Optional.ofNullable(texts)
                        .orElse(Collections.emptyList())
                        .forEach(text -> this.appendRawLine(lineMapper.apply(text)));
                this.addRawLineBreak();
                return this;
            }

            private void appendRawLine(String line)
            {
                this.stringBuilder.append(line);
                this.addRawLineBreak();
            }

            @Override
            public MarkdownDocument build()
            {
                return new MarkdownDocument() {
                    @Override
                    public String get()
                    {
                        return stringBuilder.toString();
                    }

                    @Override
                    public MarkdownParsedDocument parse()
                    {
                        return MarkdownUtils.parse(this.get());
                    }

                    @Override
                    public MarkdownParsedDocument parse(Consumer<MarkdownParseOptions> optionsConsumer)
                    {
                        return MarkdownUtils.parse(this.get(), optionsConsumer);
                    }

                    @Override
                    public MarkdownDocument writeTo(File file)
                    {
                        FileUtils.toConsumer(file)
                                 .accept(this);
                        return this;
                    }
                };
            }

            @Override
            public MarkdownDocumentBuilder addText(String text)
            {
                this.appendRawLine(text);
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addParagraph(Consumer<MarkdownParagraphBuilder> paragraphBuilderConsumer)
            {
                MarkdownDocumentBuilder documentBuilder = this;

                this.addRawLineBreak();
                MarkdownParagraphBuilder paragraphBuilder = new MarkdownParagraphBuilder() {
                    @Override
                    public MarkdownParagraphBuilder addText(String text)
                    {
                        documentBuilder.addText(text);
                        return this;
                    }

                    @Override
                    public MarkdownParagraphBuilder addTexts(String... texts)
                    {
                        documentBuilder.addTexts(texts);
                        return this;
                    }

                    @Override
                    public <E> MarkdownParagraphBuilder process(Stream<E> elements, BiConsumer<E, MarkdownParagraphBuilder> elementAndBuilderConsumer)
                    {
                        documentBuilder.process(elements, (element, document) -> elementAndBuilderConsumer.accept(element, this));
                        return this;
                    }

                    @Override
                    public <E> MarkdownParagraphBuilder process(Collection<E> elements, BiConsumer<E, MarkdownParagraphBuilder> elementAndBuilderConsumer)
                    {
                        documentBuilder.process(elements, (element, document) -> elementAndBuilderConsumer.accept(element, this));
                        return this;
                    }
                };
                paragraphBuilderConsumer.accept(paragraphBuilder);
                this.addRawLineBreak();

                return this;
            }

            private MarkdownDocumentBuilder addRawLineBreak()
            {
                this.stringBuilder.append(this.lineBreakCharacter);
                return this;
            }

            @Override
            public MarkdownDocumentBuilder withLineBreakCharacter(String lineBreakCharacter)
            {
                this.lineBreakCharacter = lineBreakCharacter;
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addTable(org.omnaest.utils.table.Table table)
            {
                final String PIPE = "|";
                final String PIPE_REPLACEMENT = " ";
                this.addRawLineBreak();
                this.appendRawLine(PIPE + table.getEffectiveColumns()
                                               .stream()
                                               .map(org.omnaest.utils.table.domain.Column::getTitle)
                                               .map(value -> StringUtils.replace(value, PIPE, PIPE_REPLACEMENT))
                                               .map(StringUtils::defaultString)
                                               .collect(Collectors.joining(PIPE))
                                   + PIPE);
                this.appendRawLine(PIPE + table.getEffectiveColumns()
                                               .stream()
                                               .map(org.omnaest.utils.table.domain.Column::getTitle)
                                               .map(content -> StringUtils.repeat("-", Math.max(3, StringUtils.length(content))))
                                               .collect(Collectors.joining(PIPE))
                                   + PIPE);
                table.getRows()
                     .stream()
                     .forEach(row -> this.appendRawLine(PIPE + row.stream()
                                                                  .map(StringUtils::defaultString)
                                                                  .map(value -> StringUtils.replace(value, PIPE, PIPE_REPLACEMENT))
                                                                  .collect(Collectors.joining(PIPE))
                                                        + PIPE));
                return this;
            }

            @Override
            public <E> MarkdownDocumentBuilder process(Stream<E> elements, BiConsumer<E, MarkdownDocumentBuilder> elementAndBuilderConsumer)
            {
                Optional.ofNullable(elements)
                        .orElse(Stream.empty())
                        .forEach(element -> elementAndBuilderConsumer.accept(element, this));
                return this;
            }

            @Override
            public <E> MarkdownDocumentBuilder process(Collection<E> elements, BiConsumer<E, MarkdownDocumentBuilder> elementAndBuilderConsumer)
            {
                return this.process(Optional.ofNullable(elements)
                                            .orElse(Collections.emptyList())
                                            .stream(),
                                    elementAndBuilderConsumer);
            }

            @Override
            public MarkdownDocumentBuilder addTexts(String... texts)
            {
                Optional.ofNullable(texts)
                        .map(Arrays::asList)
                        .orElse(Collections.emptyList())
                        .forEach(this::addText);
                return this;
            }

            @Override
            public MarkdownDocumentBuilder addLineBreak()
            {
                this.appendRawLine("\\");
                return this;
            }

            @Override
            public MarkdownDocumentBuilder applyTo(Consumer<MarkdownDocumentBuilder> builderConsumer)
            {
                if (builderConsumer != null)
                {
                    builderConsumer.accept(this);
                }
                return this;
            }

            @Override
            public MarkdownDocumentBuilder applyToIf(boolean condition, Consumer<MarkdownDocumentBuilder> builderConsumer)
            {
                if (condition && builderConsumer != null)
                {
                    builderConsumer.accept(this);
                }
                return this;
            }

        };

    }
}
