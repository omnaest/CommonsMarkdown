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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.utils.StringUtils;
import java.util.function.Consumer;

import org.omnaest.utils.markdown.MarkdownUtils.BasicList;
import org.omnaest.utils.markdown.MarkdownUtils.Code;
import org.omnaest.utils.markdown.MarkdownUtils.CodeBlock;
import org.omnaest.utils.markdown.MarkdownUtils.CustomIdentifier;
import org.omnaest.utils.markdown.MarkdownUtils.Element;
import org.omnaest.utils.markdown.MarkdownUtils.Heading;
import org.omnaest.utils.markdown.MarkdownUtils.Html;
import org.omnaest.utils.markdown.MarkdownUtils.HtmlBlock;
import org.omnaest.utils.markdown.MarkdownUtils.Image;
import org.omnaest.utils.markdown.MarkdownUtils.MarkdownDocumentBuilder;
import org.omnaest.utils.markdown.MarkdownUtils.OrderedList;
import org.omnaest.utils.markdown.MarkdownUtils.Table.Alignment;
import org.omnaest.utils.markdown.MarkdownUtils.TaskListMarker;
import org.omnaest.utils.markdown.MarkdownUtils.Table.Column;
import org.omnaest.utils.markdown.MarkdownUtils.ThematicBreak;
import org.omnaest.utils.markdown.MarkdownUtils.UnorderedList;
import org.omnaest.utils.markdown.MarkdownUtils.LineBreak;
import org.omnaest.utils.markdown.MarkdownUtils.Link;
import org.omnaest.utils.markdown.MarkdownUtils.MarkdownDocument;
import org.omnaest.utils.markdown.MarkdownUtils.MarkdownParsedDocument;
import org.omnaest.utils.markdown.MarkdownUtils.Paragraph;
import org.omnaest.utils.markdown.MarkdownUtils.Text;
import org.omnaest.utils.table.Table;

/**
 * @see MarkdownUtils
 * @author omnaest
 */
public class MarkdownUtilsTest
{

    @Test
    @Ignore
    public void testParse() throws Exception
    {
        MarkdownUtils.parse("# Some title\nThis is *strong* but\nalso *weak* as far as I go")
                     .get()
                     .forEach(element ->
                     {
                         element.asText()
                                .ifPresent(text ->
                                {
                                    System.out.print(text.getValue());
                                });
                         element.asHeading()
                                .ifPresent(heading ->
                                {
                                    System.out.println(heading.getText());
                                });
                         element.asLineBreak()
                                .ifPresent(lb ->
                                {
                                    System.out.println();
                                });
                     });
    }

    @Test
    public void testParseHeader() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# Title")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.iterator()
                                   .next()
                                   .asHeading()
                                   .isPresent());
        assertEquals("Title", elements.iterator()
                                      .next()
                                      .asHeading()
                                      .get()
                                      .getText());
        assertEquals(1, elements.iterator()
                                .next()
                                .asHeading()
                                .get()
                                .getStrength());
    }

    @Test
    public void testParseHeaderWithLink() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# [Title](\\#anker)")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        Heading heading = elements.iterator()
                                  .next()
                                  .asHeading()
                                  .get();
        assertEquals("Title", heading.getText());
        assertEquals("#anker", heading.getLinks()
                                      .get(0)
                                      .getLink());
        assertEquals(1, heading.getStrength());
    }

    @Test
    public void testParseHeaderWithCustomId() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# Title{#anker}", options -> options.enableParseCustomIdTokens())
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        Heading heading = elements.iterator()
                                  .next()
                                  .asHeading()
                                  .get();
        assertEquals("Title", heading.getText());
        assertEquals("#anker", heading.getCustomIds()
                                      .get(0));
        assertEquals(1, heading.getStrength());
    }

    @Test
    public void testParseLinkWithCustomId() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("[Title{BUTTON}](abc)", options -> options.enableParseCustomIdTokens())
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        Link link = elements.iterator()
                            .next()
                            .asLink()
                            .get();
        assertEquals("Title", link.getLabel());
        assertEquals("BUTTON", link.getCustomIds()
                                   .get(0));
        assertEquals("abc", link.getLink());
    }

    @Test
    public void testParseAndSerializeWithoutCustomId() throws Exception
    {
        MarkdownDocument markdownDocument = MarkdownUtils.builder()
                                                         .addHeading("Title{#anker}")
                                                         .addLink("Label{BUTTON}", "http://link", "tooltip")
                                                         .build();
        {
            List<Element> elements = markdownDocument.parse(options -> options.enableParseCustomIdTokens())
                                                     .get()
                                                     .collect(Collectors.toList());
            assertEquals(2, elements.size());

            Heading heading = elements.stream()
                                      .findFirst()
                                      .flatMap(Element::asHeading)
                                      .get();
            Link link = elements.stream()
                                .skip(1)
                                .findFirst()
                                .flatMap(Element::asLink)
                                .get();

            assertEquals("Title", heading.getText());
            assertEquals("#anker", heading.getCustomIds()
                                          .get(0));

            assertEquals("Label", link.getLabel());
            assertEquals("BUTTON", link.getCustomIds()
                                       .get(0));
            assertEquals("http://link", link.getLink());
            assertEquals("tooltip", link.getTooltip());
        }
        {
            List<Element> elements = markdownDocument.parse(options -> options.enableParseCustomIdTokens())
                                                     .clearCustomTokens()
                                                     .get()
                                                     .collect(Collectors.toList());
            assertEquals(2, elements.size());

            Heading heading = elements.stream()
                                      .findFirst()
                                      .flatMap(Element::asHeading)
                                      .get();
            Link link = elements.stream()
                                .skip(1)
                                .findFirst()
                                .flatMap(Element::asLink)
                                .get();

            assertEquals("Title", heading.getText());
            assertEquals(0, heading.getCustomIds()
                                   .size());

            assertEquals("Label", link.getLabel());
            assertEquals(0, link.getCustomIds()
                                .size());
            assertEquals("http://link", link.getLink());
            assertEquals("tooltip", link.getTooltip());
        }
    }

    @Test
    public void testParseHeaderWithImage() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# ![Title](image.png)")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        Heading heading = elements.iterator()
                                  .next()
                                  .asHeading()
                                  .get();
        assertEquals("Title", heading.getText());
        assertEquals("image.png", heading.getImages()
                                         .get(0)
                                         .getLink());
        assertEquals(1, heading.getStrength());
    }

    @Test
    public void testParseHeaderWithLinkAndImage() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# [Title](\\#anker)![](image.png)")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        Heading heading = elements.iterator()
                                  .next()
                                  .asHeading()
                                  .get();
        assertEquals("Title", heading.getText());
        assertEquals("#anker", heading.getLinks()
                                      .get(0)
                                      .getLink());
        assertEquals("image.png", heading.getImages()
                                         .get(0)
                                         .getLink());
        assertEquals(1, heading.getStrength());
    }

    @Test
    public void testParseText() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("This is a text\nand this is the second line")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(3, elements.size());
        assertEquals(true, elements.get(0)
                                   .asText()
                                   .isPresent());
        assertEquals(true, elements.get(1)
                                   .asLineBreak()
                                   .isPresent());
        assertEquals(true, elements.get(2)
                                   .asText()
                                   .isPresent());
        assertEquals("This is a text", elements.get(0)
                                               .asText()
                                               .get()
                                               .getValue());
        assertEquals("and this is the second line", elements.get(2)
                                                            .asText()
                                                            .get()
                                                            .getValue());

    }

    @Test
    public void testParseParagraphText() throws Exception
    {
        //
        List<Element> elements = MarkdownUtils.parse("This is a text\nand this is the second line\n\nAnother line",
                                                     options -> options.enableWrapIntoParagraphs())
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(2, elements.size());
        assertEquals(true, elements.get(0)
                                   .asParagraph()
                                   .isPresent());
        assertEquals(true, elements.get(1)
                                   .asParagraph()
                                   .isPresent());
        assertEquals(3, elements.get(0)
                                .asParagraph()
                                .get()
                                .getElements()
                                .size());
        assertEquals(1, elements.get(1)
                                .asParagraph()
                                .get()
                                .getElements()
                                .size());

        //
        List<Element> elementsOfFirstParagraph = elements.get(0)
                                                         .asParagraph()
                                                         .get()
                                                         .getElements();
        assertEquals(3, elementsOfFirstParagraph.size());
        assertEquals(true, elementsOfFirstParagraph.get(0)
                                                   .asText()
                                                   .isPresent());
        assertEquals(true, elementsOfFirstParagraph.get(1)
                                                   .asLineBreak()
                                                   .isPresent());
        assertEquals(true, elementsOfFirstParagraph.get(2)
                                                   .asText()
                                                   .isPresent());
        assertEquals("This is a text", elementsOfFirstParagraph.get(0)
                                                               .asText()
                                                               .get()
                                                               .getValue());
        assertEquals("and this is the second line", elementsOfFirstParagraph.get(2)
                                                                            .asText()
                                                                            .get()
                                                                            .getValue());

        //
        List<Element> elementsOfSecondParagraph = elements.get(1)
                                                          .asParagraph()
                                                          .get()
                                                          .getElements();
        assertEquals(1, elementsOfSecondParagraph.size());
        assertEquals(true, elementsOfSecondParagraph.get(0)
                                                    .asText()
                                                    .isPresent());
        assertEquals("Another line", elementsOfSecondParagraph.get(0)
                                                              .asText()
                                                              .get()
                                                              .getValue());

    }

    @Test
    public void testParseImage() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("![Title](/image.png \"Tooltip\")")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asImage()
                                   .isPresent());

        assertEquals("Title", elements.get(0)
                                      .asImage()
                                      .get()
                                      .getLabel());
        assertEquals("Tooltip", elements.get(0)
                                        .asImage()
                                        .get()
                                        .getTooltip());
        assertEquals("/image.png", elements.get(0)
                                           .asImage()
                                           .get()
                                           .getLink());

    }

    @Test
    public void testParseLink() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("[Link](http://somelink.org \"Tooltip\")")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.iterator()
                                   .next()
                                   .asLink()
                                   .isPresent());
        assertEquals("Link", elements.iterator()
                                     .next()
                                     .asLink()
                                     .get()
                                     .getLabel());
        assertEquals("http://somelink.org", elements.iterator()
                                                    .next()
                                                    .asLink()
                                                    .get()
                                                    .getLink());
        assertEquals("Tooltip", elements.iterator()
                                        .next()
                                        .asLink()
                                        .get()
                                        .getTooltip());
    }

    @Test
    public void testParseUnorderedList() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("- first line\n- second line")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asUnorderedList()
                                   .isPresent());

        assertEquals(2, elements.get(0)
                                .asUnorderedList()
                                .get()
                                .getElements()
                                .size());
        assertEquals("first line", elements.get(0)
                                           .asUnorderedList()
                                           .get()
                                           .getElements()
                                           .get(0)
                                           .asText()
                                           .get()
                                           .getValue());
        assertEquals("second line", elements.get(0)
                                            .asUnorderedList()
                                            .get()
                                            .getElements()
                                            .get(1)
                                            .asText()
                                            .get()
                                            .getValue());

    }

    @Test
    public void testParseOrderedList() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("1. first line\n2. second line")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asOrderedList()
                                   .isPresent());

        assertEquals(2, elements.get(0)
                                .asOrderedList()
                                .get()
                                .getElements()
                                .size());
        assertEquals("first line", elements.get(0)
                                           .asOrderedList()
                                           .get()
                                           .getElements()
                                           .get(0)
                                           .asText()
                                           .get()
                                           .getValue());
        assertEquals("second line", elements.get(0)
                                            .asOrderedList()
                                            .get()
                                            .getElements()
                                            .get(1)
                                            .asText()
                                            .get()
                                            .getValue());

    }

    @Test
    public void testParseTable() throws Exception
    {
        String text = StringUtils.builder()
                                 .addLine("| First Header     | Second Header   |")
                                 .addLine("| ---------------- | --------------- |")
                                 .addLine("| Content Cell A1  | Content Cell B1 |")
                                 .addLine("| Content Cell A2  | Content Cell B2 |")
                                 .build();
        List<Element> elements = MarkdownUtils.parse(text)
                                              .get()
                                              .collect(Collectors.toList());

        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asTable()
                                   .isPresent());

        Table table = elements.get(0)
                              .asTable()
                              .get()
                              .asStringTable();
        assertEquals(Table.newInstance()
                          .addColumnTitles("First Header", "Second Header")
                          .addRow("Content Cell A1", "Content Cell B1")
                          .addRow("Content Cell A2", "Content Cell B2"),
                     table);

    }

    @Test
    public void testParseTableWithEmptyCells() throws Exception
    {
        String text = MarkdownUtils.builder()
                                   .addTable(Table.newInstance()
                                                  .addColumnTitles("First Header", "Second Header")
                                                  .addRow("", "Content Cell B1")
                                                  .addRow("Content Cell A2", ""))
                                   .build()
                                   .get();
        String expectedText = StringUtils.builder()
                                         .withLineSeparator("\n")
                                         .addLineBreak()
                                         .addLine("|First Header|Second Header|")
                                         .addLine("|------------|-------------|")
                                         .addLine("||Content Cell B1|")
                                         .addLine("|Content Cell A2||")
                                         .build();
        assertEquals(expectedText, text);
        List<Element> elements = MarkdownUtils.parse(text)
                                              .get()
                                              .collect(Collectors.toList());

        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asTable()
                                   .isPresent());

        Table table = elements.get(0)
                              .asTable()
                              .get()
                              .asStringTable();
        assertEquals("", table.getValue(0, 0));
        assertEquals("", table.getValue(1, 1));
        assertEquals(Table.newInstance()
                          .addColumnTitles("First Header", "Second Header")
                          .addRow("", "Content Cell B1")
                          .addRow("Content Cell A2", ""),
                     table);

    }

    @Test
    public void testParseTable2() throws Exception
    {
        String text = StringUtils.builder()
                                 .addLine("| {GRID}First Header     | Second Header   |")
                                 .addLine("| ---- | ---- |")
                                 .addLine("| ![Image Title](image.png) | Content Cell B1 |")
                                 .addLine("| Content Cell A2  | Content Cell B2 |")
                                 .build();
        List<Element> elements = MarkdownUtils.parse(text, options -> options.enableParseCustomIdTokens())
                                              .get()
                                              .collect(Collectors.toList());

        assertEquals(1, elements.size());
        assertEquals(true, elements.get(0)
                                   .asTable()
                                   .isPresent());
        assertEquals("GRID", elements.get(0)
                                     .asTable()
                                     .get()
                                     .getCustomIds()
                                     .findFirst()
                                     .orElse(null));
        Table table = elements.get(0)
                              .asTable()
                              .get()
                              .asStringTable();
        assertEquals(Table.newInstance()
                          .addColumnTitles("First Header", "Second Header")
                          .addRow("", "Content Cell B1")
                          .addRow("Content Cell A2", "Content Cell B2"),
                     table);

    }

    @Test
    public void testBuildHeading()
    {
        MarkdownDocument document = MarkdownUtils.builder()
                                                 .addHeading("Heading")
                                                 .build();
        assertEquals("Heading", document.parse()
                                        .findFirst(Heading.class)
                                        .get()
                                        .getText());
        assertEquals(1, document.parse()
                                .findFirst(Heading.class)
                                .get()
                                .getStrength());
    }

    @Test
    public void testBuildParagraphAndText()
    {
        MarkdownDocument document = MarkdownUtils.builder()
                                                 .addParagraph(paragraph -> paragraph.addText("123"))
                                                 .build();
        assertEquals("123", document.parse(options -> options.enableWrapIntoParagraphs())
                                    .findFirst(Paragraph.class)
                                    .get()
                                    .getElements()
                                    .stream()
                                    .findFirst()
                                    .get()
                                    .asText()
                                    .get()
                                    .getValue());

    }

    @Test
    public void testBuildTable()
    {
        MarkdownDocument document = MarkdownUtils.builder()
                                                 .addLineBreak()
                                                 .addTable(Table.newInstance()
                                                                .addColumnTitles("Column1", "Column2")
                                                                .addRow("value1_1", "value1_2")
                                                                .addRow("value2_1", "value2_2"))
                                                 .build();
        assertEquals(Arrays.asList("Column1", "Column2"), document.parse(options -> options.enableWrapIntoParagraphs())
                                                                  .findFirst(MarkdownUtils.Table.class)
                                                                  .get()
                                                                  .getColumns()
                                                                  .stream()
                                                                  .map(column -> column.getElements()
                                                                                       .stream()
                                                                                       .findFirst()
                                                                                       .get()
                                                                                       .asText()
                                                                                       .get()
                                                                                       .getValue())
                                                                  .collect(Collectors.toList()));
        assertEquals(Arrays.asList("value1_1", "value1_2", "value2_1", "value2_2"), document.parse()
                                                                                            .findFirst(MarkdownUtils.Table.class)
                                                                                            .get()
                                                                                            .getRows()
                                                                                            .stream()
                                                                                            .flatMap(row -> row.getCells()
                                                                                                               .stream())
                                                                                            .map(cell -> cell.getElements()
                                                                                                             .stream()
                                                                                                             .findFirst()
                                                                                                             .get()
                                                                                                             .asText()
                                                                                                             .get()
                                                                                                             .getValue())
                                                                                            .collect(Collectors.toList()));

    }

    @Test
    public void testBuildTableWithoutColumnHeader()
    {
        MarkdownDocument document = MarkdownUtils.builder()
                                                 .addTable(Table.newInstance()
                                                                .addRow("value1_1", "value1_2")
                                                                .addRow("value2_1", "value2_2"))
                                                 .build();
        assertEquals(Arrays.asList("value1_1", "value1_2", "value2_1", "value2_2"), document.parse()
                                                                                            .findFirst(MarkdownUtils.Table.class)
                                                                                            .get()
                                                                                            .getRows()
                                                                                            .stream()
                                                                                            .flatMap(row -> row.getCells()
                                                                                                               .stream())
                                                                                            .map(cell -> cell.getElements()
                                                                                                             .stream()
                                                                                                             .findFirst()
                                                                                                             .get()
                                                                                                             .asText()
                                                                                                             .get()
                                                                                                             .getValue())
                                                                                            .collect(Collectors.toList()));

    }

    @Test
    public void testNextLineCharacter()
    {
        assertEquals("abc\ndef\n", MarkdownUtils.builder()
                                                .withLineBreakCharacter("\n")
                                                .addText("abc")
                                                .addText("def")
                                                .build()
                                                .get());
        assertEquals("abc\r\ndef\r\n", MarkdownUtils.builder()
                                                    .withLineBreakCharacter("\r\n")
                                                    .addText("abc")
                                                    .addText("def")
                                                    .build()
                                                    .get());
    }

    @Test
    public void testProcessor()
    {
        MarkdownDocument document = MarkdownUtils.builder()
                                                 .addParagraph(paragraph -> paragraph.addText("123")
                                                                                     .addText("abc"))
                                                 .build();
        StringBuilder stringBuilder = new StringBuilder();
        document.parse()
                .newProcessor()
                .addVisitor(Text.class, text -> stringBuilder.append(text.getValue()))
                .process();
        assertEquals("123abc", stringBuilder.toString());
    }

    @Test
    public void testAddLineBreak() throws Exception
    {
        String markdown = MarkdownUtils.builder()
                                       .withLineBreakCharacter("\n")
                                       .addText("abc")
                                       .addLineBreak()
                                       .addText("def")
                                       .build()
                                       .get();
        assertEquals("abc\n\\\ndef\n", markdown);
        MarkdownParsedDocument parsedDocument = MarkdownUtils.parse(markdown);
        assertTrue(parsedDocument.findFirst(LineBreak.class)
                                 .isPresent());

        // the builder writes the explicit break as a line of its own, so the markdown holds a soft break after "abc" and a hard break for the backslash line
        List<LineBreak> lineBreaks = MarkdownUtils.parse(markdown)
                                                  .getAndFilter(LineBreak.class)
                                                  .collect(Collectors.toList());
        assertEquals(2, lineBreaks.size());
        assertEquals(Arrays.asList(false, true), lineBreaks.stream()
                                                           .map(LineBreak::isHard)
                                                           .collect(Collectors.toList()));
    }

    @Test
    public void testParseEmphasis() throws Exception
    {
        assertEquals(Arrays.asList("italic:false|bold:false"), this.parseTextStyles("plain"));
        assertEquals(Arrays.asList("italic:true|bold:false"), this.parseTextStyles("*italic*"));
        assertEquals(Arrays.asList("italic:true|bold:false"), this.parseTextStyles("_italic_"));
        assertEquals(Arrays.asList("italic:false|bold:true"), this.parseTextStyles("**bold**"));
        assertEquals(Arrays.asList("italic:false|bold:true"), this.parseTextStyles("__bold__"));
        assertEquals(Arrays.asList("italic:false|bold:true", "italic:true|bold:true", "italic:false|bold:true"),
                     this.parseTextStyles("**bold *and italic* again**"));
    }

    @Test
    public void testParseEmphasisAroundNestedElement() throws Exception
    {
        // the label of the link is parsed by an own visitor, but the surrounding emphasis still applies to it, so the label text in the middle is italic, too
        assertEquals(Arrays.asList("italic:true|bold:false", "italic:true|bold:false", "italic:true|bold:false"),
                     this.parseTextStyles("*italic [label](http://link.example) tail*"));
        assertEquals(Arrays.asList("italic:false|bold:true"), this.parseTextStyles("# **bold heading**"));
    }

    private List<String> parseTextStyles(String markdown)
    {
        return MarkdownUtilsTest.collectContentElements(MarkdownUtils.parse(markdown)
                                                                     .get())
                                .stream()
                                .map(Element::asText)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(text -> "italic:" + text.isItalic() + "|bold:" + text.isBold())
                                .collect(Collectors.toList());
    }

    @Test
    public void testParseInlineCode() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("some `inlineCode()` here")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(3, elements.size());
        assertEquals("inlineCode()", elements.get(1)
                                             .asCode()
                                             .get()
                                             .getValue());
    }

    @Test
    public void testParseInlineCodeWithinLinkLabel() throws Exception
    {
        Link link = MarkdownUtils.parse("[`code` label](http://link.example)")
                                 .findFirst(Link.class)
                                 .get();
        assertEquals("code label", link.getLabel());
    }

    @Test
    public void testParseFencedCodeBlock() throws Exception
    {
        CodeBlock codeBlock = MarkdownUtils.parse("```java\nint value = 1;\n```\n")
                                           .findFirst(CodeBlock.class)
                                           .get();
        assertEquals("int value = 1;\n", codeBlock.getValue());
        assertEquals("java", codeBlock.getLanguage()
                                      .get());
    }

    @Test
    public void testParseFencedCodeBlockWithoutLanguage() throws Exception
    {
        CodeBlock codeBlock = MarkdownUtils.parse("```\nint value = 1;\n```\n")
                                           .findFirst(CodeBlock.class)
                                           .get();
        assertEquals("int value = 1;\n", codeBlock.getValue());
        assertEquals(false, codeBlock.getLanguage()
                                     .isPresent());
    }

    @Test
    public void testParseIndentedCodeBlock() throws Exception
    {
        CodeBlock codeBlock = MarkdownUtils.parse("    int value = 1;\n")
                                           .findFirst(CodeBlock.class)
                                           .get();
        assertEquals("int value = 1;\n", codeBlock.getValue());
        assertEquals(false, codeBlock.getLanguage()
                                     .isPresent());
    }

    @Test
    public void testParseHardAndSoftLineBreak() throws Exception
    {
        assertEquals(Arrays.asList(true), this.parseLineBreakHardness("line one  \nline two"));
        assertEquals(Arrays.asList(true), this.parseLineBreakHardness("line one\\\nline two"));
        assertEquals(Arrays.asList(false), this.parseLineBreakHardness("line one\nline two"));
    }

    private List<Boolean> parseLineBreakHardness(String markdown)
    {
        return MarkdownUtils.parse(markdown)
                            .getAndFilter(LineBreak.class)
                            .map(LineBreak::isHard)
                            .collect(Collectors.toList());
    }

    @Test
    public void testParseCustomIdTokens() throws Exception
    {
        assertEquals(Arrays.asList("GRID"), this.parseCustomIds("{GRID}Header"));
        assertEquals(Arrays.asList("#anker"), this.parseCustomIds("# Title{#anker}"));
        assertEquals(Arrays.asList("BUTTON"), this.parseCustomIds("[Title{BUTTON}](abc)"));
    }

    @Test
    public void testParseKeepsOrdinaryCurlyBraces() throws Exception
    {
        // only a single word within curly braces is a custom id token, everything else stays part of the content
        Arrays.asList("Config example: {\"a\":1} done", "before {} after", "see {@code null} here", "a {token with spaces} b")
              .forEach(markdown ->
              {
                  assertEquals("Custom id parsed from: " + markdown, Arrays.asList(), this.parseCustomIds(markdown));
                  assertEquals("Content lost in: " + markdown, markdown, MarkdownUtils.parse(markdown, options -> options.enableParseCustomIdTokens())
                                                                                      .findFirst(Text.class)
                                                                                      .get()
                                                                                      .getValue());
              });
    }

    private List<String> parseCustomIds(String markdown)
    {
        return MarkdownUtilsTest.collectContentElements(MarkdownUtils.parse(markdown, options -> options.enableParseCustomIdTokens())
                                                                    .get())
                                .stream()
                                .map(Element::asCustomIdentifier)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(CustomIdentifier::getIdentifier)
                                .collect(Collectors.toList());
    }

    /**
     * Characterization test over the whole commonmark surface: every construct below carries a marker word, and none of them may get lost on the way through
     * the parser. A construct the visitor does not know is dropped silently, so this test is the guard against that.
     */
    @Test
    public void testParseKeepsContentOfAllMarkdownConstructs() throws Exception
    {
        String markdown = "# headingText{#headingId}\n" + "\n"
                + "Some paragraphText with **boldText** and *italicText* and `inlineCodeText` and a [linkLabel](http://link.example) and an "
                + "![imageAltText](image.example.png).\n" + "\n" + "> quotedText\n" + "\n" + "* firstItemText\n" + "* secondItemText\n" + "\n"
                + "1. orderedItemText\n" + "\n" + "```java\n" + "fencedCodeText\n" + "```\n" + "\n" + "    indentedCodeText\n" + "\n" + "***\n" + "\n"
                + "<div>htmlBlockText</div>\n" + "\n" + "Text with <b>inlineHtmlText</b> tags.\n" + "\n" + "Some ~~struckText~~ and www.autolink.example\n"
                + "\n" + "- [ ] todoItemText\n" + "- [x] doneItemText\n" + "\n" + "|columnTitleText|\n" + "|---|\n" + "|cellText|\n";
        List<String> markers = Arrays.asList("headingText", "headingId", "paragraphText", "boldText", "italicText", "inlineCodeText", "linkLabel",
                                             "http://link.example", "imageAltText", "image.example.png", "quotedText", "firstItemText", "secondItemText",
                                             "orderedItemText", "fencedCodeText", "indentedCodeText", "htmlBlockText", "inlineHtmlText", "struckText",
                                             "www.autolink.example", "todoItemText", "doneItemText", "columnTitleText", "cellText");

        Arrays.asList(MarkdownUtils.parse(markdown, options -> options.enableParseCustomIdTokens()),
                      MarkdownUtils.parse(markdown, options -> options.enableParseCustomIdTokens()
                                                                      .enableWrapIntoParagraphs()))
              .forEach(parsedDocument ->
              {
                  String content = MarkdownUtilsTest.collectContent(parsedDocument.get());
                  markers.forEach(marker -> assertTrue("Content lost by the parser: " + marker + " within <" + content + ">", content.contains(marker)));
              });
    }

    @Test
    public void testParseStrikethrough() throws Exception
    {
        assertEquals(Arrays.asList("plain:false", "struckText:true", "tail:false"), MarkdownUtils.parse("plain~~struckText~~tail")
                                                                                                 .getAndFilter(Text.class)
                                                                                                 .map(text -> text.getValue() + ":" + text.isStrikethrough())
                                                                                                 .collect(Collectors.toList()));
    }

    @Test
    public void testParseStrikethroughCombinedWithEmphasis() throws Exception
    {
        Text text = MarkdownUtils.parse("~~**struckAndBold**~~")
                                 .findFirst(Text.class)
                                 .get();
        assertEquals(true, text.isStrikethrough());
        assertEquals(true, text.isBold());
    }

    @Test
    public void testParseTaskListItems() throws Exception
    {
        List<Element> elements = MarkdownUtilsTest.collectContentElements(MarkdownUtils.parse("- [ ] todoText\n- [x] doneText\n",
                                                                                              options -> options.enableWrapIntoParagraphs())
                                                                                       .get());
        assertEquals(Arrays.asList(false, true), elements.stream()
                                                         .map(Element::asTaskListMarker)
                                                         .filter(Optional::isPresent)
                                                         .map(Optional::get)
                                                         .map(TaskListMarker::isChecked)
                                                         .collect(Collectors.toList()));
        // the marker is no longer part of the text of the item
        assertEquals(Arrays.asList("todoText", "doneText"), elements.stream()
                                                                    .map(Element::asText)
                                                                    .filter(Optional::isPresent)
                                                                    .map(Optional::get)
                                                                    .map(Text::getValue)
                                                                    .map(String::trim)
                                                                    .filter(value -> !value.isEmpty())
                                                                    .collect(Collectors.toList()));
    }

    @Test
    public void testParseAutolinkOfBareUrl() throws Exception
    {
        Link link = MarkdownUtils.parse("Visit www.example.org for more")
                                 .findFirst(Link.class)
                                 .get();
        assertEquals("http://www.example.org", link.getLink());
        assertEquals("www.example.org", link.getLabel());
    }

    @Test
    public void testParseAutolinkOfBareEmailAndFullUrl() throws Exception
    {
        assertEquals("https://example.org/path", MarkdownUtils.parse("See https://example.org/path here")
                                                              .findFirst(Link.class)
                                                              .get()
                                                              .getLink());
        assertEquals("mailto:join@example.org", MarkdownUtils.parse("Write to join@example.org please")
                                                             .findFirst(Link.class)
                                                             .get()
                                                             .getLink());
    }

    @Test
    public void testParseHtmlBlockAndInlineHtml() throws Exception
    {
        assertEquals("<div class=\"x\">\n<p>htmlBlockText</p>\n</div>", MarkdownUtils.parse("<div class=\"x\">\n<p>htmlBlockText</p>\n</div>\n")
                                                                                       .findFirst(HtmlBlock.class)
                                                                                       .get()
                                                                                       .getValue());
        assertEquals(Arrays.asList("<b>", "</b>"), MarkdownUtils.parse("text with <b>bold</b> tag")
                                                                .getAndFilter(Html.class)
                                                                .map(Html::getValue)
                                                                .collect(Collectors.toList()));
    }

    @Test
    public void testParseThematicBreak() throws Exception
    {
        assertEquals(1, MarkdownUtils.parse("before\n\n***\n\nafter\n")
                                     .getAndFilter(ThematicBreak.class)
                                     .count());
    }

    @Test
    public void testParseOrderedListStartNumber() throws Exception
    {
        assertEquals(5, MarkdownUtils.parse("5. fifth\n6. sixth\n")
                                     .findFirst(OrderedList.class)
                                     .get()
                                     .getStartNumber());
        assertEquals(1, MarkdownUtils.parse("1. first\n")
                                     .findFirst(OrderedList.class)
                                     .get()
                                     .getStartNumber());
    }

    @Test
    public void testParseListTightness() throws Exception
    {
        assertEquals(true, MarkdownUtils.parse("* a\n* b\n")
                                        .findFirst(UnorderedList.class)
                                        .get()
                                        .isTight());
        assertEquals(false, MarkdownUtils.parse("* a\n\n* b\n")
                                         .findFirst(UnorderedList.class)
                                         .get()
                                         .isTight());
    }

    @Test
    public void testParseTableColumnAlignment() throws Exception
    {
        List<Optional<Alignment>> alignments = MarkdownUtils.parse("|a|b|c|d|\n|:--|:-:|--:|--|\n|1|2|3|4|\n")
                                                            .findFirst(MarkdownUtils.Table.class)
                                                            .get()
                                                            .getColumns()
                                                            .stream()
                                                            .map(Column::getAlignment)
                                                            .collect(Collectors.toList());
        assertEquals(Arrays.asList(Optional.of(Alignment.LEFT), Optional.of(Alignment.CENTER), Optional.of(Alignment.RIGHT), Optional.empty()), alignments);
    }

    @Test
    public void testBuildAndParseImage() throws Exception
    {
        Image image = MarkdownUtils.builder()
                                   .addImage("imageLabel", "image.example.png")
                                   .build()
                                   .parse()
                                   .findFirst(Image.class)
                                   .get();
        assertEquals("imageLabel", image.getLabel());
        assertEquals("image.example.png", image.getLink());
    }

    @Test
    public void testBuildAndParseEmphasis() throws Exception
    {
        assertEquals(true, this.buildAndParseFirstText(builder -> builder.addBoldText("boldText"))
                               .isBold());
        assertEquals(true, this.buildAndParseFirstText(builder -> builder.addItalicText("italicText"))
                               .isItalic());
    }

    private Text buildAndParseFirstText(Consumer<MarkdownDocumentBuilder> builderConsumer)
    {
        return MarkdownUtils.builder()
                            .applyTo(builderConsumer)
                            .build()
                            .parse()
                            .findFirst(Text.class)
                            .get();
    }

    @Test
    public void testBuildAndParseCode() throws Exception
    {
        assertEquals("inlineCode()", MarkdownUtils.builder()
                                                  .addCode("inlineCode()")
                                                  .build()
                                                  .parse()
                                                  .findFirst(Code.class)
                                                  .get()
                                                  .getValue());
    }

    @Test
    public void testBuildAndParseCodeBlock() throws Exception
    {
        CodeBlock codeBlock = MarkdownUtils.builder()
                                           .addCodeBlock("int value = 1;", "java")
                                           .build()
                                           .parse()
                                           .findFirst(CodeBlock.class)
                                           .get();
        assertEquals("int value = 1;\n", codeBlock.getValue());
        assertEquals("java", codeBlock.getLanguage()
                                      .get());
    }

    @Test
    public void testBuildAndParseUnorderedList() throws Exception
    {
        assertEquals(Arrays.asList("firstItem", "secondItem"), this.buildAndParseListTexts(builder -> builder.addUnorderedList(Arrays.asList("firstItem",
                                                                                                                                            "secondItem")),
                                                                                          UnorderedList.class));
    }

    @Test
    public void testBuildAndParseOrderedList() throws Exception
    {
        assertEquals(Arrays.asList("firstItem", "secondItem"), this.buildAndParseListTexts(builder -> builder.addOrderedList(Arrays.asList("firstItem",
                                                                                                                                          "secondItem")),
                                                                                          OrderedList.class));
    }

    private <L extends BasicList> List<String> buildAndParseListTexts(Consumer<MarkdownDocumentBuilder> builderConsumer, Class<L> listType)
    {
        return MarkdownUtils.builder()
                            .applyTo(builderConsumer)
                            .build()
                            .parse(options -> options.enableWrapIntoParagraphs())
                            .findFirst(listType)
                            .get()
                            .getElements()
                            .stream()
                            .map(element -> MarkdownUtilsTest.collectContent(Stream.of(element))
                                                             .trim())
                            .collect(Collectors.toList());
    }

    @Test
    public void testBuildAndParseBlockQuote() throws Exception
    {
        // a block quote is not modelled as an own element yet, so its content arrives as regular text
        assertTrue(MarkdownUtilsTest.collectContent(MarkdownUtils.builder()
                                                                 .addBlockQuote(Arrays.asList("quotedText"))
                                                                 .build()
                                                                 .parse()
                                                                 .get())
                                    .contains("quotedText"));
    }

    @Test
    public void testBuildAndParseStrikethrough() throws Exception
    {
        Text text = MarkdownUtils.builder()
                                 .addStrikethroughText("struckText")
                                 .build()
                                 .parse()
                                 .findFirst(Text.class)
                                 .get();
        assertEquals("struckText", text.getValue());
        assertEquals(true, text.isStrikethrough());
    }

    @Test
    public void testBuildAndParseTaskList() throws Exception
    {
        Map<String, Boolean> textToChecked = new LinkedHashMap<>();
        textToChecked.put("todoText", false);
        textToChecked.put("doneText", true);
        List<Element> elements = MarkdownUtilsTest.collectContentElements(MarkdownUtils.builder()
                                                                                       .addTaskList(textToChecked)
                                                                                       .build()
                                                                                       .parse(options -> options.enableWrapIntoParagraphs())
                                                                                       .get());
        assertEquals(Arrays.asList(false, true), elements.stream()
                                                         .map(Element::asTaskListMarker)
                                                         .filter(Optional::isPresent)
                                                         .map(Optional::get)
                                                         .map(TaskListMarker::isChecked)
                                                         .collect(Collectors.toList()));
    }

    @Test
    public void testBuildAndParseThematicBreak() throws Exception
    {
        assertEquals(1, MarkdownUtils.builder()
                                     .addText("before")
                                     .addThematicBreak()
                                     .addText("after")
                                     .build()
                                     .parse()
                                     .getAndFilter(ThematicBreak.class)
                                     .count());
    }

    @Test
    public void testParseSourceLineOfLinkAndText() throws Exception
    {
        String markdown = "# Title\n" + "\n" + "First paragraph.\n" + "\n" + "Second paragraph with a [label](http://link.example).\n";
        MarkdownParsedDocument parsedDocument = MarkdownUtils.parse(markdown, options -> options.enableWrapIntoParagraphs());

        assertEquals(Optional.of(5), MarkdownUtilsTest.collectContentElements(parsedDocument.get())
                                                      .stream()
                                                      .map(Element::asLink)
                                                      .filter(Optional::isPresent)
                                                      .map(Optional::get)
                                                      .findFirst()
                                                      .get()
                                                      .getSourceLine());
        // heading, first paragraph, then the text before the link, the link label itself and the text after it - all three on line 5
        assertEquals(Arrays.asList(1, 3, 5, 5, 5), MarkdownUtilsTest.collectContentElements(parsedDocument.get())
                                                                 .stream()
                                                                 .map(Element::asText)
                                                                 .filter(Optional::isPresent)
                                                                 .map(Optional::get)
                                                                 .map(Text::getSourceLine)
                                                                 .filter(Optional::isPresent)
                                                                 .map(Optional::get)
                                                                 .collect(Collectors.toList()));
    }

    @Test
    public void testSourceLineSurvivesClearCustomTokens() throws Exception
    {
        Link link = MarkdownUtils.parse("\n\n[label{ID}](http://link.example)\n", options -> options.enableParseCustomIdTokens())
                                 .clearCustomTokens()
                                 .findFirst(Link.class)
                                 .get();
        assertEquals(Optional.of(3), link.getSourceLine());
    }

    /**
     * Exercises {@link MarkdownParsedDocument#clearCustomTokens()} - and with it the {@link Element#cloneAndFilter(java.util.function.Predicate)} of every
     * {@link Element} type - over a document holding all markdown constructs: no custom id token may survive the filtering and no content may get lost by it.
     */
    @Test
    public void testClearCustomTokensOfAllMarkdownConstructs() throws Exception
    {
        String markdown = "# headingText{#headingId}\n" + "\n" + "Some paragraphText{paragraphId} with a [linkLabel{linkId}](http://link.example).\n" + "\n"
                + "* firstItemText{itemId}\n" + "\n" + "```java\n" + "fencedCodeText\n" + "```\n" + "\n" + "|columnTitleText{columnId}|\n" + "|---|\n"
                + "|cellText{cellId}|\n";
        List<String> markers = Arrays.asList("headingText", "paragraphText", "linkLabel", "firstItemText", "fencedCodeText", "columnTitleText", "cellText");

        MarkdownParsedDocument parsedDocument = MarkdownUtils.parse(markdown, options -> options.enableParseCustomIdTokens()
                                                                                                .enableWrapIntoParagraphs());
        assertEquals(Arrays.asList("#headingId", "paragraphId", "linkId", "itemId", "columnId", "cellId"),
                     MarkdownUtilsTest.collectContentElements(parsedDocument.get())
                                      .stream()
                                      .map(Element::asCustomIdentifier)
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .map(CustomIdentifier::getIdentifier)
                                      .collect(Collectors.toList()));

        List<Element> clearedElements = parsedDocument.clearCustomTokens()
                                                      .get()
                                                      .collect(Collectors.toList());
        assertEquals(Arrays.asList(), MarkdownUtilsTest.collectContentElements(clearedElements.stream())
                                                       .stream()
                                                       .map(Element::asCustomIdentifier)
                                                       .filter(Optional::isPresent)
                                                       .map(Optional::get)
                                                       .collect(Collectors.toList()));

        String content = MarkdownUtilsTest.collectContent(clearedElements.stream());
        markers.forEach(marker -> assertTrue("Content lost by clearCustomTokens: " + marker + " within <" + content + ">", content.contains(marker)));
    }

    /**
     * Collects everything the public API of the given {@link Element}s exposes as content. Content that is not reachable this way is lost for any caller.
     */
    private static String collectContent(Stream<Element> elements)
    {
        return MarkdownUtilsTest.collectContentElements(elements)
                                .stream()
                                .map(MarkdownUtilsTest::determineContent)
                                .collect(Collectors.joining("\n"));
    }

    private static String determineContent(Element element)
    {
        StringBuilder result = new StringBuilder();
        element.asText()
               .ifPresent(text -> result.append(text.getValue()));
        element.asCode()
               .ifPresent(code -> result.append(code.getValue()));
        element.asCodeBlock()
               .ifPresent(codeBlock -> result.append(codeBlock.getValue()));
        element.asHtml()
               .ifPresent(html -> result.append(html.getValue()));
        element.asHtmlBlock()
               .ifPresent(htmlBlock -> result.append(htmlBlock.getValue()));
        element.asCustomIdentifier()
               .ifPresent(customIdentifier -> result.append(customIdentifier.getIdentifier()));
        element.asImage()
               .ifPresent(image -> result.append(Optional.ofNullable(image.getLabel())
                                                         .orElse(""))
                                         .append(" ")
                                         .append(image.getLink()));
        element.asLink()
               .ifPresent(link -> result.append(link.getLink()));
        return result.toString();
    }

    private static List<Element> collectContentElements(Stream<Element> elements)
    {
        List<Element> result = new ArrayList<>();
        elements.forEach(element -> MarkdownUtilsTest.collectContentElements(element, result));
        return result;
    }

    private static void collectContentElements(Element element, List<Element> result)
    {
        result.add(element);
        element.asLink()
               .ifPresent(link -> link.getElements()
                                      .forEach(child -> MarkdownUtilsTest.collectContentElements(child, result)));
        element.asHeading()
               .ifPresent(heading -> heading.getElements()
                                            .forEach(child -> MarkdownUtilsTest.collectContentElements(child, result)));
        element.asElementWithChildren()
               .ifPresent(elementWithChildren -> elementWithChildren.getChildren()
                                                                    .forEach(child -> MarkdownUtilsTest.collectContentElements(child, result)));
    }
}
