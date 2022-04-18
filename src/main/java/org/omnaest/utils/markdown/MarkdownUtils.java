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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import org.omnaest.utils.FileUtils;
import org.omnaest.utils.JSONHelper;
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
            return new MarkdownParsedDocumentImpl(this.elements.stream()
                                                               .map(element -> element.cloneAndFilter(clonedElement -> !clonedElement.asCustomIdentifier()
                                                                                                                                     .isPresent()))
                                                               .filter(PredicateUtils.filterNonEmptyOptional())
                                                               .map(MapperUtils.mapOptionalToValue())
                                                               .collect(Collectors.toList()));
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
                return Optional.ofNullable(new Row(this.cells.stream()
                                                             .map(element -> element.cloneAndFilter(inclusionFilter))
                                                             .filter(PredicateUtils.filterNonEmptyOptional())
                                                             .map(MapperUtils.mapOptionalToValue())
                                                             .collect(Collectors.toList())))
                               .filter(inclusionFilter);
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

            @Override
            public Optional<Cell> cloneAndFilter(Predicate<Element> inclusionFilter)
            {
                return Optional.ofNullable(new Cell(this.getElements()
                                                        .stream()
                                                        .map(element -> element.cloneAndFilter(inclusionFilter))
                                                        .filter(PredicateUtils.filterNonEmptyOptional())
                                                        .map(MapperUtils.mapOptionalToValue())
                                                        .collect(Collectors.toList())))
                               .filter(inclusionFilter);
            }
        }

        public static class Column implements ElementWithChildren
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

            @Override
            public List<Element> getChildren()
            {
                return this.getElements();
            }

            @Override
            public Optional<? extends Column> cloneAndFilter(Predicate<Element> inclusionFilter)
            {
                return Optional.ofNullable(new Column(this.elements.stream()
                                                                   .map(element -> element.cloneAndFilter(inclusionFilter))
                                                                   .filter(PredicateUtils.filterNonEmptyOptional())
                                                                   .map(MapperUtils.mapOptionalToValue())
                                                                   .collect(Collectors.toList())))
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
            return Optional.ofNullable(new Table(this.rows.stream()
                                                          .map(row -> row.cloneAndFilter(inclusionFilter))
                                                          .filter(PredicateUtils.filterNonEmptyOptional())
                                                          .map(MapperUtils.mapOptionalToValue())
                                                          .collect(Collectors.toList()),
                                                 this.columns.stream()
                                                             .map(column -> column.cloneAndFilter(inclusionFilter))
                                                             .filter(PredicateUtils.filterNonEmptyOptional())
                                                             .map(MapperUtils.mapOptionalToValue())
                                                             .collect(Collectors.toList())))
                           .filter(inclusionFilter);
        }

    }

    public static class LineBreak implements Element
    {
        @Override
        public String toString()
        {
            return "LineBreak []";
        }

        @Override
        public Optional<LineBreak> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.ofNullable(new LineBreak())
                           .filter(inclusionFilter);
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

        @Override
        public Optional<Text> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.ofNullable(new Text(this.value, this.bold))
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
            return Optional.ofNullable(new CustomIdentifier(this.identifier))
                           .filter(inclusionFilter);
        }
    }

    public static class BasicList implements ElementWithChildren
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
        public List<Element> getChildren()
        {
            return this.getElements();
        }

        @Override
        public String toString()
        {
            return "BasicList [elements=" + this.elements + "]";
        }

        @Override
        public Optional<? extends BasicList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.ofNullable(new BasicList(this.elements.stream()
                                                                  .map(element -> element.cloneAndFilter(inclusionFilter))
                                                                  .filter(PredicateUtils.filterNonEmptyOptional())
                                                                  .map(MapperUtils.mapOptionalToValue())
                                                                  .collect(Collectors.toList())))
                           .filter(inclusionFilter);
        }
    }

    public static class UnorderedList extends BasicList
    {
        public UnorderedList(List<Element> elements)
        {
            super(elements);
        }

        @Override
        public Optional<UnorderedList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.ofNullable(new UnorderedList(this.getElements()
                                                             .stream()
                                                             .map(element -> element.cloneAndFilter(inclusionFilter))
                                                             .filter(PredicateUtils.filterNonEmptyOptional())
                                                             .map(MapperUtils.mapOptionalToValue())
                                                             .collect(Collectors.toList())))
                           .filter(inclusionFilter);
        }
    }

    public static class OrderedList extends BasicList
    {
        public OrderedList(List<Element> elements)
        {
            super(elements);
        }

        @Override
        public Optional<OrderedList> cloneAndFilter(Predicate<Element> inclusionFilter)
        {
            return Optional.ofNullable(new OrderedList(this.getElements()
                                                           .stream()
                                                           .map(element -> element.cloneAndFilter(inclusionFilter))
                                                           .filter(PredicateUtils.filterNonEmptyOptional())
                                                           .map(MapperUtils.mapOptionalToValue())
                                                           .collect(Collectors.toList())))
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
            return Optional.ofNullable(new Heading(this.level, this.getElements()
                                                                   .stream()
                                                                   .map(element -> element.cloneAndFilter(inclusionFilter))
                                                                   .filter(PredicateUtils.filterNonEmptyOptional())
                                                                   .map(MapperUtils.mapOptionalToValue())
                                                                   .collect(Collectors.toList())))
                           .filter(inclusionFilter);
        }

    }

    public static class Link implements Element
    {
        private String        link;
        private String        tooltip;
        private List<Element> elements;

        public Link(String link, List<Element> elements, String tooltip)
        {
            super();
            this.link = link;
            this.elements = elements;
            this.tooltip = tooltip;
        }

        public String getLink()
        {
            return this.link;
        }

        public String getLabel()
        {
            return Optional.ofNullable(this.elements)
                           .orElse(Collections.emptyList())
                           .stream()
                           .map(Element::asText)
                           .filter(PredicateUtils.filterNonEmptyOptional())
                           .map(MapperUtils.mapOptionalToValue())
                           .map(Text::getValue)
                           .collect(Collectors.joining());
        }

        public List<String> getCustomIds()
        {
            return this.elements.stream()
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
            return Optional.ofNullable(new Link(this.link, this.elements.stream()
                                                                        .map(element -> element.cloneAndFilter(inclusionFilter))
                                                                        .filter(PredicateUtils.filterNonEmptyOptional())
                                                                        .map(MapperUtils.mapOptionalToValue())
                                                                        .collect(Collectors.toList()),
                                                this.tooltip))
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
            return Optional.ofNullable(new Paragraph(this.elements.stream()
                                                                  .map(element -> element.cloneAndFilter(inclusionFilter))
                                                                  .filter(PredicateUtils.filterNonEmptyOptional())
                                                                  .map(MapperUtils.mapOptionalToValue())
                                                                  .collect(Collectors.toList())))
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
            return Optional.ofNullable(new Image(this.link, this.label, this.tooltip))
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

        @Override
        public MarkdownParseOptions clone()
        {
            return JSONHelper.clone(this);
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
        List<Extension> extensions = Arrays.asList(TablesExtension.create());
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

        private boolean childrenProcessing = true;

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
            String nonInterpretableValue = this.options.isParseCustomIdTokens() ? MatcherUtils.interpreter()
                                                                                              .ifContainsRegEx("\\{([^\\}]*)\\}",
                                                                                                               customIdMatch -> customIdMatch.getSubGroupsAsStream()
                                                                                                                                             .forEach(group -> this.elementConsumer.accept(new CustomIdentifier(group))))
                                                                                              .apply(value)
                    : value;
            this.elementConsumer.accept(new Text(nonInterpretableValue, this.bold));
            super.visit(text);
        }

        @Override
        public void visit(org.commonmark.node.Heading heading)
        {
            int level = heading.getLevel();
            List<Element> elements = new ArrayList<>();
            new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(heading);
            this.elementConsumer.accept(new Heading(level, elements));
        }

        @Override
        public void visit(org.commonmark.node.Link link)
        {
            //            AtomicReference<String> label = new AtomicReference<>();
            //            link.accept(new AbstractVisitor()
            //            {
            //                @Override
            //                public void visit(org.commonmark.node.Text text)
            //                {
            //                    label.updateAndGet(previous -> Optional.ofNullable(previous)
            //                                                           .orElse("")
            //                            + text.getLiteral());
            //                }
            //            });
            List<Element> elements = new ArrayList<>();
            new ElementConsumerDrivenVisitor(elements::add, this.options).visitChildren(link);
            this.elementConsumer.accept(new Link(link.getDestination(), elements, link.getTitle()));
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
        return new MarkdownDocumentBuilder()
        {
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

            private void appendRawLine(String line)
            {
                this.stringBuilder.append(line);
                this.addRawLineBreak();
            }

            @Override
            public MarkdownDocument build()
            {
                return new MarkdownDocument()
                {
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
                MarkdownParagraphBuilder paragraphBuilder = new MarkdownParagraphBuilder()
                {
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
