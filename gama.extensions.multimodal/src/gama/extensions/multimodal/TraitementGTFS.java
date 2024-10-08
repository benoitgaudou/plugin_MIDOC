package gama.extensions.multimodal;

import gama.core.common.geometry.Envelope3D;
import gama.core.runtime.IScope;
import gama.core.runtime.exceptions.GamaRuntimeException;
import gama.core.util.GamaListFactory;
import gama.core.util.IList;
import gama.core.util.file.GamaFile;
import gama.gaml.types.IType;
import gama.gaml.types.Types;
import gama.gaml.types.IContainerType;
import gama.annotations.precompiler.IConcept;
import gama.annotations.precompiler.GamlAnnotations.doc;
import gama.annotations.precompiler.GamlAnnotations.example;
import gama.annotations.precompiler.GamlAnnotations.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@file(
        name = "gtfs",
        extensions = { "txt" },
        buffer_type = IType.LIST,
        buffer_content = IType.STRING,
        buffer_index = IType.INT,
        concept = { IConcept.FILE },
        doc = @doc("GTFS files represent public transportation data in CSV format, typically with the '.txt' extension.")
)

/**
 * This class handles GTFS data files in GAMA.
 * It reads multiple GTFS text files and provides access to their content.
 */

//TraitementGTFS hérite Gamafile pour ouvrir, lire, et manipuler des fichiers GTFS.
public class TraitementGTFS extends GamaFile<IList<String>, String> {
	
	//Déclaration d'une liste de fichiers obligatoires 
	 private static final String[] REQUIRED_FILES = {
		        "agency.txt", "routes.txt", "trips.txt", "calendar.txt", "stop_times.txt", "stops.txt"
		    };
	

	//Créer des maps qui contient une pair valeurs: String:nom du fichier
	//  String:nom du fichier, Ilist<String> listes de chaînes de caractères. ex: 
	//["stop_id,stop_name,stop_lat,stop_lon", 
	//"1,Main Street,45.123,-73.987", 
	//"2,Second Street,45.124,-73.988"]
    private Map<String, IList<String>> gtfsData; 

    /**
     * Constructor for reading GTFS files.
     *
     * @param scope    The scope of the simulation.
     * @param pathName The file path or directory of the GTFS files.
     * @throws GamaRuntimeException If there is an issue loading the file.
     */

    @doc (
			value = "Ce constructeur permet de charger les fichiers GTFS à partir d'un répertoire spécifié.",
			examples = { @example (value = "TraitementGTFS gtfs <- TraitementGTFS(scope, \"path_to_gtfs_directory\");")})
    //scope qui représente le contexte de la simulation dans GAMA. Il est utilisé pour la gestion des ressources dans GAMA
    //String pathName : Le chemin du répertoire contenant les fichiers GTFS.
    public TraitementGTFS(final IScope scope, final String pathName) throws GamaRuntimeException {
    	//Appeler constructeur parent de GamaFile 
        super(scope, pathName);
        //et la méthode loadGtfsFiles
        loadGtfsFiles(scope, pathName);
    }

    /**
     * Loads GTFS data from the specified file path.
     *
     * @param scope    The scope of the simulation.
     * @param pathName The path to the GTFS file or directory.
     * @throws GamaRuntimeException If there is an error reading the file.
     */
    //
    private void loadGtfsFiles(final IScope scope, final String pathName) throws GamaRuntimeException {
        File folder = new File(pathName);
      //Vérifie si le chemin passé pathName est un répertoire valide
        if (!folder.exists() || !folder.isDirectory()) {
      //Si ce n’est pas le cas, une GamaRuntimeException est levée
            throw GamaRuntimeException.error("The provided GTFS file path is invalid.", scope);
        }
        // Création des HashMap pour stocker les données de chaque fichier.
        gtfsData = new HashMap<>(); 
        
        // Vérification des fichiers obligatoires
        Set<String> requiredFilesSet = new HashSet<>(Arrays.asList(REQUIRED_FILES));

        try {
        	//utilise la méthode listFiles() pour obtenir un tableau de File représentant tous les fichiers 
            File[] files = folder.listFiles();
            if (files != null) {
            	//Parcourt tous les fichiers du dossier.
                for (File file : files) {
                	// Récupération du nom de chaque fichier
                    String fileName = file.getName();
                    //Vérification des fichiers .txt
                    if (fileName.endsWith(".txt")) {
                    	//Lecture du fichier avec readCsvFile
                        IList<String> fileContent = readCsvFile(file);
                        //Stockage du contenu dans gtfsData
                        gtfsData.put(fileName, fileContent); 
                        
                        // Supprimer le fichier du set des fichiers obligatoires s'il est présent
                        requiredFilesSet.remove(fileName);
                    }
                }
            }
            
            // Si certains fichiers obligatoires manquent, lancer une exception
            if (!requiredFilesSet.isEmpty()) {
                throw GamaRuntimeException.error("Missing required GTFS files: " + requiredFilesSet, scope);
            }

        } catch (Exception e) {
            throw GamaRuntimeException.create(e, scope);
        }
    }

    /**
     * Reads a CSV file (GTFS files are in CSV format) and returns the content as an IList.
     *
     * @param file The GTFS file to read.
     * @return A list of rows as strings.
     * @throws IOException If there is an error reading the file.
     */
    
    //La méthode retourne une liste d'éléments de type String.
    //La méthode readCsvFile prend en entrée un objet de type File (fichier GTFS csv)
    private IList<String> readCsvFile(File file) throws IOException {
    	//Crée une liste vide de chaînes de caractères IList 
        IList<String> content = GamaListFactory.create(); 
        //Lecture du fichier à l'aide de BufferedReader
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        	//Lecture ligne par ligne du fichier
            String line;
            while ((line = br.readLine()) != null) {
            	//Ajoute chaque ligne lue dans la liste
                content.add(line); 
            }
        }
        return content;
    }

    @Override
    //La méthode fillBuffer est utilisée pour s'assurer que le contenu des fichiers GTFS est bien chargé dans la mémoire 
    protected void fillBuffer(final IScope scope) throws GamaRuntimeException {
        if (gtfsData == null) {
        	// Chargement des fichiers GTFS
            loadGtfsFiles(scope, getPath(scope)); 
        }
    }

    @Override
    //récupérer une liste des noms de fichiers GTFS 
    public IList<String> getAttributes(final IScope scope) {
        // Récupération des clés de gtfsData
        Set<String> keySet = gtfsData.keySet();
        //La méthode retourne une IList<String>, qui contient les noms de fichiers sous la forme d'une liste de chaînes
        return GamaListFactory.createWithoutCasting(Types.STRING, keySet.toArray(new String[0]));
    }

    @Override
    public IContainerType<IList<String>> getGamlType() {
        // Retourne un type FILE avec des contenus de chaîne de caractères
        return Types.FILE.of(Types.STRING, Types.STRING);
    }



    /**
     * Computes an envelope of the GTFS file (optional method for spatial computations).
     *
     * @param scope The scope of the simulation.
     * @return null since spatial computation is not implemented here.
     */
    @Override
    public Envelope3D computeEnvelope(final IScope scope) {
        // Not implemented: Return null since GTFS files are not directly spatial
        return null;
    }

    // Supprimer cette méthode si elle est déjà redondante
    // @Override
    // public String getGamlType(final IScope scope) {
    //     return "GTFS file";
    // }
}
