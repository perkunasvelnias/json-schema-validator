/*
 * Copyright (c) 2012, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.syntax.draftv3;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.report.Message;
import com.github.fge.jsonschema.syntax.AbstractSyntaxChecker;
import com.github.fge.jsonschema.syntax.SyntaxValidator;
import com.github.fge.jsonschema.util.NodeType;
import com.google.common.collect.Sets;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Dedicated syntax checker for {@code type} and {@code disallow}
 *
 * <p>These keywords are monsters. Only {@code dependencies} comes close in
 * terms of complexity.</p>
 */
public final class DraftV3TypeKeywordSyntaxChecker
    extends AbstractSyntaxChecker
{
    private static final String ANY = "any";
    private static final EnumSet<NodeType> VALID_TYPE_ARRAY_ELEMENTS
        = EnumSet.of(NodeType.OBJECT, NodeType.STRING);

    public DraftV3TypeKeywordSyntaxChecker(final String keyword)
    {
        super(keyword, NodeType.STRING, NodeType.ARRAY);
    }

    @Override
    public void checkValue(final SyntaxValidator validator,
        final List<Message> messages, final JsonNode schema)
    {
        final JsonNode node = schema.get(keyword);
        final Message.Builder msg = newMsg();

        if (!node.isArray()) {
            validateOne(validator, msg, messages, node);
            return;
        }

        final Set<JsonNode> set = Sets.newHashSet();

        int index = 0;
        for (final JsonNode value: node) {
            msg.clearInfo().addInfo("index", index);
            index++;
            if (!set.add(value)) {
                msg.setMessage("duplicate value found in array");
                messages.add(msg.build());
                continue;
            }
            validateOne(validator, msg, messages, value);
        }
    }

    private static void validateOne(final SyntaxValidator validator,
        final Message.Builder msg, final List<Message> messages,
        final JsonNode value)
    {
        // Cannot happen in the event of single property validation (will
        // always be a string)
        if (value.isObject()) {
            validator.validate(messages, value);
            return;
        }

        // See above
        if (!value.isTextual()) {
            msg.addInfo("found", NodeType.getNodeType(value))
                .setMessage("array element has incorrect type")
                .addInfo("expected", VALID_TYPE_ARRAY_ELEMENTS);
            messages.add(msg.build());
            return;
        }

        // Now we can actually check that the string is a valid primitive type
        final String s = value.textValue();

        if (ANY.equals(s))
            return;

        if (NodeType.fromName(s) != null)
            return;

        msg.addInfo("possible-values", EnumSet.allOf(NodeType.class))
            .addInfo("found", s).setMessage("unknown simple type");
        messages.add(msg.build());
    }
}