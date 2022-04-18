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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.utils.StringUtils;
import org.omnaest.utils.markdown.MarkdownUtils.Element;
import org.omnaest.utils.markdown.MarkdownUtils.Heading;
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
        assertEquals(1, MarkdownUtils.parse(markdown)
                                     .getAndFilter(LineBreak.class)
                                     .count());
    }
}
