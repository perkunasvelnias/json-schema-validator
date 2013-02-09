/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
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

package com.github.fge.jsonschema.crude;

import com.github.fge.jsonschema.library.DraftV3Library;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Library;
import com.github.fge.jsonschema.library.SchemaVersion;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processing.ProcessorChain;
import com.github.fge.jsonschema.processing.ValidationData;
import com.github.fge.jsonschema.processing.build.FullValidationContext;
import com.github.fge.jsonschema.processing.build.ValidatorBuilder;
import com.github.fge.jsonschema.processing.digest.SchemaDigester;
import com.github.fge.jsonschema.processing.ref.Dereferencing;
import com.github.fge.jsonschema.processing.ref.RefResolverProcessor;
import com.github.fge.jsonschema.processing.ref.SchemaLoader;
import com.github.fge.jsonschema.processing.ref.URIManager;
import com.github.fge.jsonschema.processing.selector.ProcessorSelector;
import com.github.fge.jsonschema.processing.syntax.SyntaxProcessor;
import com.github.fge.jsonschema.processing.validation.ValidationProcessor;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.schema.SchemaBundle;
import com.github.fge.jsonschema.uri.DefaultURIDownloader;
import com.github.fge.jsonschema.util.JsonLoader;

import java.io.IOException;
import java.net.URI;

import static com.github.fge.jsonschema.library.SchemaVersion.*;

public final class CrudeValidator
{
    private final SchemaLoader loader;
    private final Processor<ValidationData, FullValidationContext> draftv4;
    private final Processor<ValidationData, FullValidationContext> draftv3;
    private final Processor<ValidationData, ProcessingReport> validator;

    public CrudeValidator(final SchemaVersion version,
        final Dereferencing dereferencing)
        throws IOException
    {
        final URIManager manager = new URIManager();
        manager.unregisterScheme("file");
        manager.unregisterScheme("resource");
        manager.unregisterScheme("jar");
        manager.registerScheme("https", DefaultURIDownloader.getInstance());

        loader = new SchemaLoader(manager, URI.create("#"), dereferencing);

        final SchemaBundle bundle = new SchemaBundle();
        bundle.addSchema(DRAFTV4.getLocation().toURI(),
            JsonLoader.fromResource("/draftv4/schema"));
        bundle.addSchema(DRAFTV3.getLocation().toURI(),
            JsonLoader.fromResource("/draftv3/schema"));

        loader.addBundle(bundle);

        draftv4 = buildProcessor(DraftV4Library.get());
        draftv3 = buildProcessor(DraftV3Library.get());

        final Processor<ValidationData, FullValidationContext> byDefault
            = version == DRAFTV4 ? draftv4 : draftv3;

        final Processor<ValidationData, FullValidationContext> processor
            = new ProcessorSelector<ValidationData, FullValidationContext>()
                .when(DRAFTV4.versionTest()).then(draftv4)
                .when(DRAFTV3.versionTest()).then(draftv3)
                .otherwise(byDefault).getProcessor();

        validator = new ValidationProcessor(processor);
    }

    private Processor<ValidationData, FullValidationContext> buildProcessor(
        final Library library)
    {
        return ProcessorChain
            .startWith(new RefResolverProcessor(loader))
            .chainWith(new SyntaxProcessor(library.getSyntaxCheckers()))
            .failOnError()
            .chainWith(new SchemaDigester(library.getDigesters()))
            .chainWith(new ValidatorBuilder(library.getValidators()))
            .end();
    }
}