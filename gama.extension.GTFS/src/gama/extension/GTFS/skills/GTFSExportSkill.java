package gama.extension.GTFS.skills;

import gama.annotations.action;
import gama.annotations.skill;
import gama.api.kernel.skill.Skill;
import gama.api.runtime.scope.IScope;
import gama.annotations.doc;

import gama.extension.GTFS.GamaGTFSFile;
import gama.extension.GTFS.export.GTFSShapeExporter;

/**
 * Skill pour exporter les shapes ou stops GTFS en shapefile via une action GAMA.
 */
@skill(
    name = "gtfs_export",
    concept = {}
)
public class GTFSExportSkill extends Skill {

    @action(
        name = "export_shapes_to_shapefile",
        doc = @doc("Exporte les shapes GTFS en shapefile routes.shp (LineString) **ou** stops_points.shp (Points) dans le dossier includes du projet, selon la présence de shapes.txt.")
    )
    public Object exportShapesToShapefile(final IScope scope) {
        try {
            // 1. Récupérer GTFS_reader déclaré globalement (ex: gtfs_f)
            GamaGTFSFile reader = (GamaGTFSFile) scope.getGlobalVarValue("gtfs_f");
            if (reader == null) {
                throw new RuntimeException("Variable globale gtfs_f non trouvée !");
            }
            // 2. Définir le dossier de sortie (ex: "includes")
            String outputPath = scope.getModel().getProjectPath() + "/includes";

            // 3. Appeler la méthode d'export automatique (LineString ou Points selon le GTFS)
            GTFSShapeExporter.exportGTFSAsShapefile(scope, reader, outputPath);

            // 4. Message de succès dans la console GAMA
            if (scope.getGui() != null) {
                scope.getGui().getConsole().informConsole("✅ Export GTFS -> Shapefile (routes.shp OU stops_points.shp) terminé dans " + outputPath, scope.getSimulation());
            }
        } catch (Exception e) {
            if (scope.getGui() != null) {
                scope.getGui().getConsole().informConsole("❌ Erreur export GTFS vers shapefile : " + e.getMessage(), scope.getSimulation());
            }
            e.printStackTrace();
        }
        return null;
    }
}
