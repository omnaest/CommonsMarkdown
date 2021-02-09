package org.omnaest.utils.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.parser.Parser;
import org.omnaest.utils.ConsumerUtils;

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

        @SuppressWarnings("unchecked")
        public default <T extends Element> Optional<T> as(Class<T> type)
        {
            return Optional.of(this)
                           .filter(element -> type.isAssignableFrom(element.getClass()))
                           .map(element -> (T) element);
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
        Parser parser = Parser.builder()
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

    }
}
