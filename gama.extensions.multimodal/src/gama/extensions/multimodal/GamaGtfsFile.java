package gama.extensions.multimodal;


import gama.core.common.geometry.Envelope3D;
import gama.core.runtime.IScope;
import gama.core.runtime.exceptions.GamaRuntimeException;
import gama.core.util.IList;
import gama.core.util.GamaListFactory;
import gama.core.util.file.GamaFile;
import gama.gaml.types.IContainerType;
import gama.gaml.types.Types;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;




public class GamaGtfsFile extends GamaFile<IList<IList<String>>, String> {

    private final IList<IList<String>> gtfsData = GamaListFactory.create();

    public GamaGtfsFile(final IScope scope, final String pathName) throws GamaRuntimeException {
        super(scope, pathName);
        loadGtfsFiles(scope, pathName);
    }

    @Override
    protected void fillBuffer(final IScope scope) throws GamaRuntimeException {
        if (!gtfsData.isEmpty()) {
            setBuffer(gtfsData);
        }
    }

    private void loadGtfsFiles(final IScope scope, final String pathName) throws GamaRuntimeException {
        try {
            readCsvFile(scope, pathName);
        } catch (IOException e) {
            throw GamaRuntimeException.create(e, scope);
        }
    }

    private void readCsvFile(final IScope scope, final String filePath) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        IList<IList<String>> fileData = GamaListFactory.create();
        while ((line = br.readLine()) != null) {
            IList<String> row = GamaListFactory.create();
            String[] values = line.split(",");
            for (String value : values) {
                row.add(value.trim());
            }
            fileData.add(row);
        }
        br.close();
        gtfsData.addAll(fileData);
    }

    @Override
    public IContainerType getGamlType() {
        // Retourner un type de conteneur qui est un fichier contenant des listes de listes de chaînes
        return Types.FILE.of(Types.LIST.of(Types.LIST.of(Types.STRING)));
    }

    @Override
    public Envelope3D computeEnvelope(final IScope scope) {
        // Retourner null si vous ne gérez pas d'enveloppe géographique
        return null;
    }
}
