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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.omnaest.utils.ConsumerUtils;
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
    public static interface Element
    {
        public default Optional<Text> asText()
        {
            return as(Text.class);
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

    }

    public static class Table implements Element
    {
        private List<Column> columns;
        private List<Row>    rows;

        public Table(List<Row> rows, List<Column> columns)
        {
            super();
            this.rows = rows;
            this.columns = columns;
        }

        public static class Row implements Element
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

        }

        public static class Cell extends Column
        {

            public Cell(List<Element> elements)
            {
                super(elements);
            }

            @Override
            public String toString()
            {
                return "Cell [getElements()=" + this.getElements() + "]";
            }

        }

        public static class Column implements Element
        {
            private List<Element> elements;

            public Column(List<Element> elements)
            {
                super();
                this.elements = elements;
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
                return "Column [elements=" + this.elements + "]";
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

    }

    public static class LineBreak implements Element
    {
        @Override
        public String toString()
        {
            return "LineBreak []";
        }
    }

    public static class Text implements Element
    {
        private String  value;
        private boolean bold;

        public Text(String value, boolean bold)
        {
            super();
            this.value = value;
            this.bold = bold;
        }

        public String getValue()
        {
            return this.value;
        }

        public boolean isBold()
        {
            return this.bold;
        }

        @Override
        public String toString()
        {
            return "Text [value=" + this.value + ", bold=" + this.bold + "]";
        }

    }

    public static class BasicList implements Element
    {
        private List<Element> elements;

        public BasicList(List<Element> elements)
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
            return "BasicList [elements=" + this.elements + "]";
        }

    }

    public static class UnorderedList extends BasicList
    {
        public UnorderedList(List<Element> elements)
        {
            super(elements);
        }
    }

    public static class OrderedList extends BasicList
    {
        public OrderedList(List<Element> elements)
        {
            super(elements);
        }
    }

    public static class Heading implements Element
    {
        private int    level;
        private String text;

        public Heading(int level, String text)
        {
            super();
            this.level = level;
            this.text = text;
        }

        public int getStrength()
        {
            return this.level;
        }

        public String getText()
        {
            return this.text;
        }

        @Override
        public String toString()
        {
            return "Heading [strength=" + this.level + ", text=" + this.text + "]";
        }

    }

    public static class Link implements Element
    {
        private String link;
        private String label;
        private String tooltip;

        public Link(String link, String label, String tooltip)
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

        public String getLabel()
        {
            return this.label;
        }

        public String getTooltip()
        {
            return this.tooltip;
        }

        @Override
        public String toString()
        {
            return "Link [link=" + this.link + ", label=" + this.label + ", tooltip=" + this.tooltip + "]";
        }

    }

    public static class Paragraph implements Element
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

    }

    public static interface MarkdownParseResult
    {
        public Stream<Element> get();
    }

    public static class MarkdownParseOptions
    {
        private boolean wrapIntoParagraphs = false;

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

    }

    public static MarkdownParseResult parse(String text)
    {
        return parse(text, ConsumerUtils.noOperation());
    }

    public static MarkdownParseResult parse(String text, Consumer<MarkdownParseOptions> optionsConsumer)
    {
        List<Extension> extensions = Arrays.asList(TablesExtension.create());
        Parser parser = Parser.builder()
                              .extensions(extensions)
                              .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                              .build();
        Node document = parser.parse(text);

        List<Element> elements = new ArrayList<>();

        //
        MarkdownParseOptions options = new MarkdownParseOptions();
        Optional.ofNullable(optionsConsumer)
                .ifPresent(consumer -> consumer.accept(options));

        //
        Consumer<Element> elementConsumer = e -> elements.add(e);
        document.accept(new ElementConsumerDrivenVisitor(elementConsumer, options));

        //
        return new MarkdownParseResult()
        {
            @Override
            public Stream<Element> get()
            {
                return elements.stream();
            }
        };
    }

    private static class ElementConsumerDrivenVisitor extends AbstractVisitor
    {
        private final Consumer<Element> elementConsumer;
        private boolean                 bold = false;
        private MarkdownParseOptions    options;

        private ElementConsumerDrivenVisitor(Consumer<Element> elementConsumer, MarkdownParseOptions options)
        {
            this.elementConsumer = elementConsumer;
            this.options = options;
        }

        @Override
        public void visit(Emphasis emphasis)
        {
            String openingDelimiter = emphasis.getOpeningDelimiter();
            boolean isBold = org.apache.commons.lang3.StringUtils.equals("*", openingDelimiter);
            if (isBold)
            {
                this.bold = true;
            }

            super.visit(emphasis);

            if (isBold)
            {
                this.bold = false;
            }
        }

        @Override
        public void visit(SoftLineBreak softLineBreak)
        {
            this.elementConsumer.accept(new LineBreak());
            super.visit(softLineBreak);
        }

        @Override
        public void visit(org.commonmark.node.Text text)
        {
            String value = text.getLiteral();
            this.elementConsumer.accept(new Text(value, this.bold));
            super.visit(text);
        }

        @Override
        public void visit(org.commonmark.node.Heading heading)
        {
            int level = heading.getLevel();
            AtomicReference<String> textHolder = new AtomicReference<>();
            new AbstractVisitor()
            {
                @Override
                public void visit(org.commonmark.node.Text text)
                {
                    textHolder.updateAndGet(currentText -> org.apache.commons.lang3.StringUtils.defaultString(currentText) + text.getLiteral());
                    super.visit(text);
                }

            }.visit(heading);
            this.elementConsumer.accept(new Heading(level, textHolder.get()));
        }

        @Override
        public void visit(org.commonmark.node.Link link)
        {
            AtomicReference<String> label = new AtomicReference<>();
            link.accept(new AbstractVisitor()
            {
                @Override
                public void visit(org.commonmark.node.Text text)
                {
                    label.updateAndGet(previous -> Optional.ofNullable(previous)
                                                           .orElse("")
                            + text.getLiteral());
                }
            });
            this.elementConsumer.accept(new Link(link.getDestination(), label.get(), link.getTitle()));
        }

        @Override
        public void visit(org.commonmark.node.Image image)
        {
            AtomicReference<String> label = new AtomicReference<>();
            image.accept(new AbstractVisitor()
            {
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
                new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(paragraph);
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
            new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(bulletList);
            this.elementConsumer.accept(new UnorderedList(elements));
        }

        @Override
        public void visit(org.commonmark.node.OrderedList orderedList)
        {
            List<Element> elements = new ArrayList<>();
            new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(orderedList);
            this.elementConsumer.accept(new OrderedList(elements));
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
                this.elementConsumer.accept(new Table.Cell(this.parseChildrenElements(customNode)));
            }
            else if (customNode instanceof TableBody)
            {
                this.parseChildrenElements(customNode)
                    .forEach(this.elementConsumer::accept);
            }
            else
            {
                super.visit(customNode);
            }
        }

        private List<Element> parseChildrenElements(Node node)
        {
            List<Element> elements = new ArrayList<>();
            new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(node);
            return elements;
        }

    }
}
