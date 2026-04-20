package gama.extension.GTFS.gaml.file.object;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.date.IDate;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;
import gama.extension.GTFS.utils.file.GTFSKeywords;
import gama.extension.GTFS.utils.file.GtfsCalendarFilter;

public class DepartureInfos {
	
	private static final Logger LOGGER = Logger.getLogger(TransportStop.class.getName());	
	
	@SuppressWarnings("unchecked")
	public static void computeDepartureInfo(
			IScope scope, 
			IMap<String, TransportTrip> tripsMap, 
			IMap<String, TransportStop> stopsMap, 
			IMap<String, List<String[]>> gtfsData, 
			IMap<String, IMap<String, Integer>> headerMaps,
			IDate startingDate,
			IDate endingDate) {
		LOGGER.info("Starting computeDepartureInfo...");

		boolean useAllTrips = ((startingDate == null) || (endingDate == null));
		// TODO
		//		IDate startingDateObj = (IDate) scope.getGlobalVarValue(GTFSKeywords.GAML_VAR_STARTING_DATE) ;

		// 2. Détermination des trips actifs selon la stratégie
		Set<String> activeTripIds;

		if (useAllTrips) {
			// CAS 3 : Utiliser TOUS les trips
			activeTripIds = new HashSet<>(tripsMap.keySet());
		} else {
			// CAS 1 & 2 : Filtrage par date (logique existante)
			activeTripIds = getActiveTripIdsForDate(scope, startingDate,endingDate);
		}

		LOGGER.info("DEBUG Java - activeTripIds.size() = " + activeTripIds.size());

		// 3. Traitement des stop_times (identique pour tous les cas)
		List<String[]> stopTimesData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_STOP_TIMES);
		IMap<String, Integer> stopTimesHeader = headerMaps.get(GTFSKeywords.FILE_STOP_TIMES);

		if (stopTimesData == null || stopTimesHeader == null) {
			LOGGER.warning("[ERROR] stop_times.txt data or headers are missing!");
			return;
		}

		Integer tripIdIndex = GamaGTFSFile.findColumnIndex(stopTimesHeader, GTFSKeywords.COL_TRIP_ID);
		Integer stopIdIndex = GamaGTFSFile.findColumnIndex(stopTimesHeader, GTFSKeywords.COL_STOP_ID);
		Integer departureTimeIndex = GamaGTFSFile.findColumnIndex(stopTimesHeader, GTFSKeywords.COL_DEPARTURE_TIME);
		Integer stopSequenceIndex = GamaGTFSFile.findColumnIndex(stopTimesHeader, GTFSKeywords.COL_STOP_SEQUENCE);

		if (tripIdIndex == null || stopIdIndex == null || departureTimeIndex == null || stopSequenceIndex == null) {
			LOGGER.warning("[ERROR] Required columns missing in stop_times.txt!");
			return;
		}

		// 4. Remplissage des trips et stops (avec filtrage conditionnel)
		int totalAdded = 0;
		int totalSkipped = 0;
		int totalMissingTrip = 0;
		int totalFilteredOut = 0; // NOUVEAU compteur

		int processedTrips = 0;
		int filteredTrips = 0;

		for (String[] fields : stopTimesData) {
			if (fields == null 
					//|| fields.length <= Math.max(Math.max(tripIdIndex, stopIdIndex),
					//Math.max(departureTimeIndex, stopSequenceIndex))) {
				) {
				totalSkipped++;
				continue;
			}

			try {
				String tripId = GamaGTFSFile.clean(fields[tripIdIndex]);

				// FILTRAGE CONDITIONNEL selon la stratégie
				if (!useAllTrips && !activeTripIds.contains(tripId)) {
					totalFilteredOut++;
					filteredTrips++;
					continue; // Skip seulement si on filtre par date
				}
				processedTrips++;
				
				String stopId = GamaGTFSFile.clean(fields[stopIdIndex]);
				String departureTime = fields[departureTimeIndex];
				//TODO
//                int stopSequence = Integer.parseInt(fields[stopSequenceIndex]);

				TransportTrip trip = tripsMap.get(tripId);
				if (trip == null) {
					totalMissingTrip++;
					continue;
				}

				trip.addStop(stopId);
				trip.addStopDetail(stopId, departureTime, 0.0);
				totalAdded++;

				TransportStop stop = stopsMap.get(stopId);
				if (stop != null) {
					int tripRouteType = trip.getRouteType();
					if (tripRouteType != -1 && stop.getRouteType() == -1) {
						stop.setRouteType(tripRouteType);
					}
					stop.addTripShapePair(tripId, trip.getShapeId()); // maintenant String → OK après 2) et 3)
				}

			} catch (Exception e) {
				LOGGER.severe("[ERROR] Échec traitement ligne : " + Arrays.toString(fields) + " → " + e.getMessage());
			}
		}

		LOGGER.info("DEBUG stop_times boucle:");
		LOGGER.info("   → Trips processés: " + processedTrips);
		LOGGER.info("   → Trips filtrés: " + filteredTrips);

		// 5. Résumé avec nouvelles métriques
		LOGGER.info("Résumé computeDepartureInfo():");
		LOGGER.info("   → Stratégie: " + (useAllTrips ? "TOUS LES TRIPS" : "FILTRAGE PAR DATE"));
		LOGGER.info("   → starting_date défini: " + startingDate);
		if (!useAllTrips) {
			LOGGER.info("   → Date de simulation: " + endingDate);
			LOGGER.info("   → Trips actifs trouvés: " + activeTripIds.size());
		}
		LOGGER.info("   → Stops ajoutés dans trips : " + totalAdded);
		LOGGER.info("   → Lignes stop_times ignorées (incomplètes) : " + totalSkipped);
		LOGGER.info("   → tripId non trouvés dans tripsMap : " + totalMissingTrip);
		LOGGER.info("   → Trips filtrés par date : " + totalFilteredOut);

		// 6. Création des departureTripsInfo (identique)
//		IMap<String, IList<IPair<String, String>>> departureTripsInfo = GamaMapFactory.create(Types.STRING, Types.LIST);
		IMap<String, IMap<String, IDate>> departureTripsInfo = GamaMapFactory.create(Types.STRING, Types.MAP);
		
		// IMPORTANT : Utiliser la même logique de filtrage ici
		Set<String> tripsToProcess = useAllTrips ? tripsMap.keySet() : activeTripIds;

		for (String tripId : tripsToProcess) {
			TransportTrip trip = tripsMap.get(tripId);
			if (trip == null)
				continue;

			IList<String> stopsInOrder = trip.getStopsInOrder();
			IList<IMap<String, Object>> stopDetails = trip.getStopDetails();
		//	IList<IPair<String, String>> stopPairs = GamaListFactory.create(Types.PAIR);
			IMap<String, IDate> stopPairs = GamaMapFactory.create(Types.STRING, Types.DATE);

			if (stopsInOrder.isEmpty() || stopDetails.size() != stopsInOrder.size())
				continue;

			for (int i = 0; i < stopsInOrder.size(); i++) {
				String stopId = stopsInOrder.get(i);
				String departureTime = stopDetails.get(i).get(GTFSKeywords.KEY_DEPARTURE_TIME).toString();
//				String departureInSeconds = GtfsCalendarFilter.convertTimeToSeconds(departureTime);
				IDate departureDate = GtfsCalendarFilter.GTFSTimeToDate(scope, departureTime);
//				stopPairs.add(GamaPairFactory.createWith(stopId, departureInSeconds, Types.STRING, Types.STRING));
				stopPairs.put(stopId, departureDate);

			}
			departureTripsInfo.put(tripId, stopPairs);
		}

		// 6. Détermination des stops de départ : prendre le plus petit stop_sequence
		// par trip
		Map<String, List<String>> stopToTripIds = new HashMap<>();
		Set<String> seenTripSignatures = new HashSet<>();

		Map<String, String> tripToFirstStop = new HashMap<>();
		Map<String, IDate> tripToFirstStopTime = new HashMap<>();
		Map<String, Integer> tripToMinSeq = new HashMap<>();

		int tripsFiltresDansStopsDepart = 0;
		int tripsTraitesDansStopsDepart = 0;

		for (String[] fields : stopTimesData) {
			if (fields == null || fields.length <= Math.max(Math.max(tripIdIndex, stopIdIndex),
					Math.max(departureTimeIndex, stopSequenceIndex))) {
				continue;
			}

			try {
				String tripId = GamaGTFSFile.clean(fields[tripIdIndex]);
				if (!useAllTrips && !activeTripIds.contains(tripId)) {
					tripsFiltresDansStopsDepart++;
					continue;
				}
				if (useAllTrips && !tripsMap.containsKey(tripId)) {
					tripsFiltresDansStopsDepart++;
					continue;
				}

				String stopId = GamaGTFSFile.clean(fields[stopIdIndex]);
//				String departureTime = fields[departureTimeIndex];
				IDate departureTime = GtfsCalendarFilter.GTFSTimeToDate(scope,fields[departureTimeIndex]);

				int seq;
				try {
					seq = Integer.parseInt(fields[stopSequenceIndex].trim());
				} catch (Exception ex) {
					// si stop_sequence manquant ou non numérique, on ignore cette ligne
					continue;
				}

				tripsTraitesDansStopsDepart++;

				Integer curMin = tripToMinSeq.get(tripId);
				if (curMin == null || seq < curMin) {
					// nouveau minimum
					tripToMinSeq.put(tripId, seq);
					tripToFirstStop.put(tripId, stopId);
//					tripToFirstStopTime.put(tripId, GtfsCalendarFilter.convertTimeToSeconds(departureTime));
					tripToFirstStopTime.put(tripId, departureTime);
				} else if (curMin != null && seq == curMin) {
					// égalité : garder le départ le plus tôt
					IDate curTime = tripToFirstStopTime.get(tripId);
//					String newTime = GtfsCalendarFilter.convertTimeToSeconds(departureTime);
					IDate newTime = departureTime;
					
//					if (curTime == null || Integer.parseInt(newTime) < Integer.parseInt(curTime)) {
					if (curTime == null || newTime.isSmallerThan(curTime, false) ) {
						tripToFirstStop.put(tripId, stopId);
						tripToFirstStopTime.put(tripId, newTime);
					}
				}

			} catch (Exception e) {
				// on ignore les erreurs de parsing ici
			}
		}

		LOGGER.info("DEBUG stops de départ:");
		LOGGER.info("   → Trips traités pour stops départ: " + tripsTraitesDansStopsDepart);
		LOGGER.info("   → Trips filtrés pour stops départ: " + tripsFiltresDansStopsDepart);
		LOGGER.info("   → Stops de départ identifiés: " + tripToFirstStop.size());

		// Utiliser les vrais stops de départ pour créer stopToTripIds
		for (String tripId : departureTripsInfo.keySet()) {
			IMap<String, IDate> stopPairs = departureTripsInfo.get(tripId);
			if (stopPairs == null || stopPairs.isEmpty())
				continue;

			// Utiliser le stop avec stop_sequence = 1 si disponible
			String firstStopId = tripToFirstStop.get(tripId);
			IDate departureTime = tripToFirstStopTime.get(tripId);

			// Fallback : si pas de stop_sequence = 1, utiliser le premier dans la liste
			if (firstStopId == null) {
				firstStopId = stopPairs.getKeys().get(0);
				departureTime = stopPairs.getValues().get(0);
				LOGGER.info("[WARNING] Trip " + tripId
						+ " n'a pas de stop_sequence=1, utilise le premier stop rencontré: " + firstStopId);
			}

			// Créer la signature pour éviter les doublons
			StringBuilder stopSequence = new StringBuilder();
			for (Entry<String, IDate> pair : stopPairs.entrySet()) {
				stopSequence.append(pair.getKey()).append(";");
			}
			String signature = firstStopId + "_" + departureTime + "_" + stopSequence;

			if (seenTripSignatures.contains(signature))
				continue;
			seenTripSignatures.add(signature);
			stopToTripIds.computeIfAbsent(firstStopId, k -> new ArrayList<>()).add(tripId);
		}

		// 7. Affectation dans chaque stop + tri + comptage
		for (Map.Entry<String, List<String>> entry : stopToTripIds.entrySet()) {
			String stopId = entry.getKey();
			List<String> tripIds = entry.getValue();

			tripIds.sort((id1, id2) -> {
				IDate t1 = tripToFirstStopTime.getOrDefault(id1, departureTripsInfo.get(id1).firstValue(scope));
				IDate t2 = tripToFirstStopTime.getOrDefault(id2, departureTripsInfo.get(id2).firstValue(scope));
//				return Integer.compare(Integer.parseInt(t1), Integer.parseInt(t2));
				return t1.compareTo(t2);
			});

			TransportStop stop = stopsMap.get(stopId);
			if (stop == null)
				continue;
			stop.ensureDepartureTripsInfo();
			for (String tripId : tripIds) {
				IMap<String, IDate> pairs = departureTripsInfo.get(tripId);
				stop.addStopPairs(tripId, pairs);
			}
			stop.setTripNumber(stop.getDepartureTripsInfo().size());
		}

		// 8. Résumé final
		int nbStopsAvecTrips = 0;
		for (TransportStop stop : stopsMap.values()) {
			if (stop.getDepartureTripsInfo() != null && !stop.getDepartureTripsInfo().isEmpty()) {
				nbStopsAvecTrips++;
			}
		}
		LOGGER.info("Nombre de stops avec departureTripsInfo non vide : " + nbStopsAvecTrips);
		LOGGER.info("Nombre de trips au total dans tripsMap : " + tripsMap.size());
		LOGGER.info("Nombre de stops de départ identifiés (stop_sequence=1) : " + tripToFirstStop.size());
		LOGGER.info("computeDepartureInfo completed successfully.");
	}    

	public List<TransportTrip> getActiveTripsForDate(IScope scope, LocalDate date, 
			Map<String, TransportTrip> tripsMap, 
			Map<String, Object> gtfsData, 
			Map<String, IMap<String, Integer>> headerMaps) {
		Set<String> activeTripIds = getActiveTripIdsForDate(scope, date, gtfsData, headerMaps);
		List<TransportTrip> activeTrips = new ArrayList<>();
		for (String tripId : activeTripIds) {
			TransportTrip trip = tripsMap.get(tripId);
			if (trip != null)
				activeTrips.add(trip);
		}
		return activeTrips;
	}

	public static Set<String> getActiveTripIdsForDate(IScope scope, IDate startingDate, IDate endingDate) {
		Set<String> activeTripIds = null;
		try {
	//		activeTripIds = getActiveTripIdsForDate(scope, date);
		} catch (Exception e) {
			LOGGER.severe("Error while retrieving active trip IDs for date " + startingDate + ": " + e.getMessage());
		}
		return activeTripIds;
	}

	private Set<String> getActiveTripIdsForDate(IScope scope, LocalDate date, 
			Map<String, Object> gtfsData, 
			Map<String, IMap<String, Integer>> headerMaps) {
		LOGGER.info("\n=== DÉBUT getActiveTripIdsForDate ===");
		LOGGER.info("Recherche trips actifs pour la date: " + date);
		LOGGER.info("Jour de la semaine: " + date.getDayOfWeek());
		LOGGER.info("Format GTFS: "
				+ date.format(java.time.format.DateTimeFormatter.ofPattern(GTFSKeywords.GTFS_DATE_FORMAT)));

		Set<String> validTripIds = new HashSet<>();
		Map<String, String> tripIdToServiceId = new HashMap<>();

		// 1. Construction de la map trip -> service_id
		LOGGER.info("\n--- Phase 1: Lecture trips.txt ---");
		List<String[]> tripsData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_TRIPS);
		IMap<String, Integer> tripsHeader = headerMaps.get(GTFSKeywords.FILE_TRIPS);

		if (tripsData == null || tripsHeader == null) {
			LOGGER.severe("[ERROR] trips.txt data or headers are missing!");
			return validTripIds;
		}

		Integer tripIdIdx = GamaGTFSFile.findColumnIndex(tripsHeader, GTFSKeywords.COL_TRIP_ID);
		Integer serviceIdIdx = GamaGTFSFile.findColumnIndex(tripsHeader, GTFSKeywords.COL_SERVICE_ID);
		if (tripIdIdx == null || serviceIdIdx == null) {
			LOGGER.severe("[ERROR] trip_id or service_id column missing in trips.txt!");
			LOGGER.info("   → trip_id index: " + tripIdIdx);
			LOGGER.info("   → service_id index: " + serviceIdIdx);
			return validTripIds;
		}

		int tripsProcessed = 0;
		int tripsIgnored = 0;
		for (String[] fields : tripsData) {
			// Ignore les lignes vides ou mal formées
			if (fields.length > Math.max(tripIdIdx, serviceIdIdx)) {
				tripIdToServiceId.put(fields[tripIdIdx].trim().replace("\"", ""),
						fields[serviceIdIdx].trim().replace("\"", ""));
				tripsProcessed++;
			} else {
				tripsIgnored++;
			}
		}
		LOGGER.info("trips.txt traitement:");
		LOGGER.info("   → Trips traités: " + tripsProcessed);
		LOGGER.info("   → Trips ignorés: " + tripsIgnored);
		LOGGER.info("   → Services uniques: " + tripIdToServiceId.values().stream().distinct().count());

		// 2. Vérification des fichiers calendrier
		System.out.println("\n--- Phase 2: Vérification fichiers calendrier ---");
		List<String[]> calendarData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR);
		List<String[]> calendarDatesData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR_DATES);
		boolean hasCalendar = (calendarData != null && !calendarData.isEmpty());
		boolean hasCalendarDates = (calendarDatesData != null && !calendarDatesData.isEmpty());

		LOGGER.info("Disponibilité fichiers:");
		LOGGER.info("   → calendar.txt: " + (hasCalendar ? "(" + calendarData.size() + " lignes)" : "X"));
		LOGGER.info(
				"   → calendar_dates.txt: " + (hasCalendarDates ? " (" + calendarDatesData.size() + " lignes)" : "X"));

		Set<String> activeServiceIds = new HashSet<>();
		java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
				.ofPattern(GTFSKeywords.GTFS_DATE_FORMAT);
		String dayOfWeek = date.getDayOfWeek().toString().toLowerCase();
		String dateString = date.format(formatter);

		LOGGER.info("Paramètres recherche:");
		LOGGER.info("   → Date: " + dateString);
		LOGGER.info("   → Jour: " + dayOfWeek);

		// 3. Traitement calendar.txt
		if (hasCalendar) {
			System.out.println("\n--- Phase 3: Traitement calendar.txt ---");
			IMap<String, Integer> calendarHeader = headerMaps.get(GTFSKeywords.FILE_CALENDAR);
			if (calendarHeader == null) {
				System.err.println("[ERROR] calendar.txt headers missing!");
			} else {
				try {
					Integer serviceIdIdxCal = GamaGTFSFile.findColumnIndex(calendarHeader, GTFSKeywords.COL_SERVICE_ID);
					Integer startIdx = GamaGTFSFile.findColumnIndex(calendarHeader, GTFSKeywords.COL_START_DATE);
					Integer endIdx = GamaGTFSFile.findColumnIndex(calendarHeader, GTFSKeywords.COL_END_DATE);
					Integer dayIdx = GamaGTFSFile.findColumnIndex(calendarHeader, dayOfWeek);

					LOGGER.info("Index des colonnes:");
					LOGGER.info("   → service_id: " + serviceIdIdxCal);
					LOGGER.info("   → start_date: " + startIdx);
					LOGGER.info("   → end_date: " + endIdx);
					LOGGER.info("   → " + dayOfWeek + ": " + dayIdx);

					if (serviceIdIdxCal == null || startIdx == null || endIdx == null || dayIdx == null) {
						LOGGER.severe("[ERROR] Some required columns are missing in calendar.txt!");
					} else {
						int servicesActifs = 0;
						int servicesInactifs = 0;
						int servicesHorsPeriode = 0;
						int servicesJourInactif = 0;

						for (String[] fields : calendarData) {
							if (fields.length <= Math.max(Math.max(serviceIdIdxCal, startIdx),
									Math.max(endIdx, dayIdx)))
								continue;

							try {
								String serviceId = fields[serviceIdIdxCal].trim().replace("\"", "");
								LocalDate start = LocalDate.parse(fields[startIdx], formatter);
								LocalDate end = LocalDate.parse(fields[endIdx], formatter);
								boolean dayActive = fields[dayIdx].equals("1");
								boolean inPeriod = !date.isBefore(start) && !date.isAfter(end);
								boolean runsToday = dayActive && inPeriod;

								if (runsToday) {
									activeServiceIds.add(serviceId);
									servicesActifs++;
								} else {
									servicesInactifs++;
									if (!inPeriod)
										servicesHorsPeriode++;
									if (!dayActive)
										servicesJourInactif++;
								}
							} catch (Exception e) {
								System.err.println("Erreur ligne calendar.txt: " + Arrays.toString(fields) + " -> "
										+ e.getMessage());
							}
						}

						LOGGER.info("Résultats calendar.txt pour " + date + ":");
						LOGGER.info("   → Services actifs: " + servicesActifs);
						LOGGER.info("   → Services inactifs: " + servicesInactifs);
						LOGGER.info("     ↳ Hors période: " + servicesHorsPeriode);
						LOGGER.info("     ↳ Jour inactif: " + servicesJourInactif);
					}
				} catch (Exception e) {
					LOGGER.severe("[ERROR] Processing calendar.txt failed: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}

		// 4. Traitement calendar_dates.txt
		if (hasCalendarDates) {
			LOGGER.info("\n--- Phase 4: Traitement calendar_dates.txt ---");
			IMap<String, Integer> calDatesHeader = headerMaps.get(GTFSKeywords.FILE_CALENDAR_DATES);
			if (calDatesHeader == null) {
				LOGGER.severe("[ERROR] calendar_dates.txt headers missing!");
			} else {
				try {
					Integer serviceIdIdxCal = GamaGTFSFile.findColumnIndex(calDatesHeader, GTFSKeywords.COL_SERVICE_ID);
					Integer dateIdx = GamaGTFSFile.findColumnIndex(calDatesHeader, GTFSKeywords.COL_DATE);
					Integer exceptionTypeIdx = GamaGTFSFile.findColumnIndex(calDatesHeader, GTFSKeywords.COL_EXCEPTION_TYPE);

					if (serviceIdIdxCal == null || dateIdx == null || exceptionTypeIdx == null) {
						LOGGER.severe("[ERROR] Some required columns are missing in calendar_dates.txt!");
					} else {
						int ajouts = 0;
						int suppressions = 0;
						int datesNonCorrespondantes = 0;

						for (String[] fields : calendarDatesData) {
							if (fields.length <= Math.max(Math.max(serviceIdIdxCal, dateIdx), exceptionTypeIdx))
								continue;

							try {
								String serviceId = fields[serviceIdIdxCal].trim().replace("\"", "");
								LocalDate exceptionDate = LocalDate.parse(fields[dateIdx], formatter);
								int exceptionType = Integer.parseInt(fields[exceptionTypeIdx]);

								if (exceptionDate.equals(date)) {
									if (exceptionType == 1) {
										activeServiceIds.add(serviceId);
										ajouts++;

									}
									if (exceptionType == 2) {
										boolean wasActive = activeServiceIds.remove(serviceId);
										suppressions++;
										LOGGER.info("➖ Service supprimé: " + serviceId
												+ " (exception_type=2, était actif: " + wasActive + ")");
									}
								} else {
									datesNonCorrespondantes++;
								}
							} catch (Exception e) {
								LOGGER.severe("Erreur ligne calendar_dates.txt: " + Arrays.toString(fields) + " -> "
										+ e.getMessage());
							}
						}

						LOGGER.info("Résultats calendar_dates.txt:");
						LOGGER.info("   → Services ajoutés (type=1): " + ajouts);
						LOGGER.info("   → Services supprimés (type=2): " + suppressions);
						LOGGER.info("   → Dates non correspondantes: " + datesNonCorrespondantes);
					}
				} catch (Exception e) {
					LOGGER.severe("[ERROR] Processing calendar_dates.txt failed: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}

		// 5. Conversion services -> trips
		LOGGER.info("\n--- Phase 5: Conversion services -> trips ---");
		LOGGER.info("Services actifs identifiés: " + activeServiceIds.size());
		if (activeServiceIds.size() <= 10) {
			LOGGER.info("Services actifs: " + activeServiceIds);
		}

		int tripsActifs = 0;
		for (Map.Entry<String, String> e : tripIdToServiceId.entrySet()) {
			if (activeServiceIds.contains(e.getValue())) {
				validTripIds.add(e.getKey());
				tripsActifs++;
			}
		}

		LOGGER.info("Conversion résultat:");
		LOGGER.info("   → Trips actifs trouvés: " + tripsActifs);

		// 6. FALLBACK SI AUCUN TRIP
		if (validTripIds.isEmpty()) {
			LOGGER.severe("\n [WARNING] AUCUN TRIP ACTIF pour la date: " + date);
			LOGGER.severe("[FALLBACK CAS 2] Recherche d'un jour équivalent dans GTFS...");

			LocalDate altDate = findFirstDateWithSameWeekDay(date, gtfsData, headerMaps);
			if (altDate != null && !altDate.equals(date)) {
				LOGGER.info("[FALLBACK CAS 2] Jour équivalent trouvé: " + altDate);
				Set<String> fallbackTrips = getActiveTripIdsForDate(scope, altDate, gtfsData, headerMaps);
				LOGGER.info("[FALLBACK CAS 2] Trips récupérés: " + fallbackTrips.size());
				return fallbackTrips;
			} else {
				LOGGER.severe("[FALLBACK CAS 2] No matching weekday found in GTFS.");
				// NE PAS faire de fallback vers tous les trips ici
				// Laissez le CAS 3 être géré dans computeDepartureInfo
			}
		}

		return validTripIds;
	}

	private LocalDate findFirstDateWithSameWeekDay(LocalDate wantedDate, 
			Map<String, Object> gtfsData, 
			Map<String, IMap<String, Integer>> headerMaps) {
		System.out.println("\n findFirstDateWithSameWeekDay appelée...");
		System.out.println(" Date recherchée: " + wantedDate + " (" + wantedDate.getDayOfWeek() + ")");

		List<LocalDate> allDates = new ArrayList<>();
		java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
				.ofPattern(GTFSKeywords.GTFS_DATE_FORMAT);

		// calendar.txt
		LOGGER.info("\n Collecte des dates depuis calendar.txt...");
		List<String[]> calendarData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR);
		if (calendarData != null && !calendarData.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR);
			if (header != null) {
				Integer startIdx = GamaGTFSFile.findColumnIndex(header, GTFSKeywords.COL_START_DATE);
				Integer endIdx = GamaGTFSFile.findColumnIndex(header, GTFSKeywords.COL_END_DATE);
				if (startIdx != null && endIdx != null) {
					int periodesTraitees = 0;
					int datesAjoutees = 0;
					for (String[] fields : calendarData) {
						if (fields.length > endIdx) {
							try {
								LocalDate start = LocalDate.parse(fields[startIdx], formatter);
								LocalDate end = LocalDate.parse(fields[endIdx], formatter);

								LOGGER.info("   Période: " + start + " → " + end);

								for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
									allDates.add(d);
									datesAjoutees++;
								}
								periodesTraitees++;
							} catch (Exception e) {
								LOGGER.severe("Erreur parsing période: " + Arrays.toString(fields));
							}
						}
					}
					LOGGER.info("calendar.txt:");
					LOGGER.info("   → Périodes traitées: " + periodesTraitees);
					LOGGER.info("   → Dates ajoutées: " + datesAjoutees);
				}
			}
		} else {
			LOGGER.info("calendar.txt non disponible");
		}

		// calendar_dates.txt
		LOGGER.info("\n Collecte des dates depuis calendar_dates.txt...");
		List<String[]> calendarDates = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR_DATES);
		if (calendarDates != null && !calendarDates.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR_DATES);
			if (header != null) {
				Integer dateIdx = GamaGTFSFile.findColumnIndex(header, GTFSKeywords.COL_DATE);
				if (dateIdx != null) {
					int datesAjoutees = 0;
					for (String[] fields : calendarDates) {
						if (fields.length > dateIdx) {
							try {
								LocalDate d = LocalDate.parse(fields[dateIdx], formatter);
								allDates.add(d);
								datesAjoutees++;
							} catch (Exception e) {
								System.err.println("Erreur parsing date: " + Arrays.toString(fields));
							}
						}
					}
					LOGGER.info("calendar_dates.txt:");
					LOGGER.info("   → Dates ajoutées: " + datesAjoutees);
				}
			}
		} else {
			LOGGER.info("calendar_dates.txt non disponible");
		}

		LOGGER.info("\n Total dates collectées: " + allDates.size());

		// Recherche du premier jour avec le même dayOfWeek
		LOGGER.info("Recherche du premier " + wantedDate.getDayOfWeek() + " disponible...");

		LocalDate firstMatch = null;
		int correspondances = 0;
		LocalDate minDate = null;
		LocalDate maxDate = null;

		for (LocalDate d : allDates) {
			// Mise à jour min/max pour debug
			if (minDate == null || d.isBefore(minDate))
				minDate = d;
			if (maxDate == null || d.isAfter(maxDate))
				maxDate = d;

			if (d.getDayOfWeek().equals(wantedDate.getDayOfWeek())) {
				correspondances++;
				if (firstMatch == null || d.isBefore(firstMatch)) {
					firstMatch = d;
					System.out.println("      → Nouveau premier match: " + firstMatch);
				}
			}
		}

		LOGGER.info("\nRésultat recherche:");
		LOGGER.info("   → Période GTFS: " + minDate + " → " + maxDate);
		LOGGER.info("   → Correspondances " + wantedDate.getDayOfWeek() + ": " + correspondances);
		LOGGER.info("   → Premier match: " + firstMatch);

		if (firstMatch != null) {
			LOGGER.info("Date de fallback choisie: " + firstMatch);
			LOGGER.info("   → Écart avec date demandée: "
					+ java.time.temporal.ChronoUnit.DAYS.between(wantedDate, firstMatch) + " jours");
		} else {
			System.out.println("Aucun jour équivalent trouvé");
		}

		return firstMatch;
	}
}
