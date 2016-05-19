package de.mpc.pia.modeller.peptide;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mpc.pia.intermediate.Accession;
import de.mpc.pia.intermediate.Modification;
import de.mpc.pia.intermediate.Peptide;
import de.mpc.pia.modeller.IdentificationKeySettings;
import de.mpc.pia.modeller.psm.PSMReportItem;
import de.mpc.pia.modeller.psm.ReportPSM;
import de.mpc.pia.modeller.psm.ReportPSMSet;
import de.mpc.pia.modeller.report.filter.Filterable;
import de.mpc.pia.modeller.score.FDRComputable;
import de.mpc.pia.modeller.score.FDRData.DecoyStrategy;
import de.mpc.pia.modeller.score.FDRScoreComputable;
import de.mpc.pia.modeller.score.ScoreModel;
import de.mpc.pia.modeller.score.ScoreModelEnum;
import de.mpc.pia.modeller.score.comparator.Rankable;


/**
 * This class holds the information of a peptide, as it will be reported in the
 * {@link PeptideViewer}
 *
 * @author julian
 *
 */
public class ReportPeptide implements Rankable, Filterable, FDRComputable, FDRScoreComputable {

    /** identifier for the peptide*/
    private String stringID;

    /** the actual sequence of the peptide */
    private String sequence;

    /** the rank of this peptide (if calculated) */
    private Long rank;

    /** the PSMs in this peptide */
    private List<PSMReportItem> psmList;

    /** list of ReportPSM IDs, which do not score in this peptide */
    private Set<Long> nonScoringPSMIDs;

    /** list of spectrum Identification keys, which do not score in this peptide */
    private Set<String> nonScoringSpectraIDKeys;


    /** set of the spectra identification keys */
    private Set<String> allSpectraKeySet;

    /**
     * this may in rare cases differ from the PSMSets (if same spectrum in
     * peptide with different PSM sets from different search engines)
     */
    private Map<String, Boolean> maximalSpectraIdentificationSettings;

    /**
     * this may in rare cases differ from the PSMSets (if same spectrum in
     * peptide with different PSM sets from different search engines)
     */
    private Map<String, Boolean> maximalNonRedundantSpectraIdentificationSettings;

    /** the intermediate peptide of this report peptide */
    private Peptide peptide;

    /** whether this peptide contains only decoy PSMs or not */
    private boolean isDecoy;

    /** the value of the local FDR for this peptide */
    private Double fdrValue;

    /** the q-value, only available when FDR is calculated */
    private Double qValue;

    /** represents whether the peptide is globally FDR good (q-value beneath the set threshold) */
    private boolean isFDRGood;

    /** the FDR Score of the peptide */
    private ScoreModel fdrScore;


    /**
     * Basic constructor
     *
     * @param sequence
     * @param stringID
     */
    public ReportPeptide(String sequence, String stringID, Peptide peptide) {
        this.sequence = sequence;
        this.stringID = stringID;
        this.peptide = peptide;
        rank = null;
        psmList = new ArrayList<PSMReportItem>();
        nonScoringPSMIDs = new HashSet<Long>();
        nonScoringSpectraIDKeys = new HashSet<String>();
        allSpectraKeySet = null;
        maximalSpectraIdentificationSettings = null;
        maximalNonRedundantSpectraIdentificationSettings = null;
        isDecoy = false;
        dumpFDRCalculation();
    }


    /**
     * Returns the identifier for this peptide.<br/>
     * This would either simply be the sequence or the sequence and
     * modification information.
     *
     * @return
     */
    public String getStringID() {
        return stringID;
    }


    /**
     * Getter for the sequence.
     * @return
     */
    public String getSequence() {
        return sequence;
    }


    /**
     * Returns the {@link Peptide} this {@link ReportPeptide} based on.
     * @return
     */
    public Peptide getPeptide() {
        return peptide;
    }


    @Override
    public void setRank(Long rank) {
        this.rank = rank;
    }


    @Override
    public Long getRank() {
        return rank;
    }


    /**
     * Getter for the missed cleavages.<br/>
     * Returns the missed cleavages of the first available PSM.<br/>
     * WARNING: this may make no sense for an overview look, but is intended to
     * be used for single files only! Different files may have different
     * protease settings.
     *
     * @return
     */
    public int getMissedCleavages() {
        if (psmList.size() > 0) {
            return psmList.get(0).getMissedCleavages();
        }

        return -1;
    }


    /**
     * Getter for the accessions
     *
     * @return
     */
    public List<Accession> getAccessions() {
        Set<String> accSet = new HashSet<String>();
        List<Accession> accList = new ArrayList<Accession>();

        for (PSMReportItem psm : psmList) {
            for (Accession acc : psm.getAccessions()) {
                if (!accSet.contains(acc.getAccession())) {
                    accSet.add(acc.getAccession());
                    accList.add(acc);
                }
            }
        }

        return accList;
    }


    /**
     * Adds a single PSM or PSM set to the map of PSMs.
     *
     * @param psm
     */
    public void addPSM(PSMReportItem psm) {
        psmList.add(psm);
        allSpectraKeySet = null;
        maximalSpectraIdentificationSettings = null;
        maximalNonRedundantSpectraIdentificationSettings = null;
    }


    /**
     * Adds the PSM with the given ID to the non scoring PSMs of this peptide.
     * @param ID
     */
    public void addToNonScoringPSMs(Long ID) {
        nonScoringPSMIDs.add(ID);
    }


    /**
     * Removes the PSM with the given ID from the non scoring PSMs of this
     * peptide.
     * @param ID
     * @return true, if the ID was in the set
     */
    public boolean removeFromNonScoringPSMs(Long ID) {
        return nonScoringPSMIDs.remove(ID);
    }


    /**
     * Returns the set of IDs from non scoring PSMs in this peptide.
     * @return
     */
    public Set<Long> getNonScoringPSMIDs() {
        return nonScoringPSMIDs;
    }


    /**
     * Removes all elements from the non scoring PSMs set.
     */
    public void clearNonScoringPSMIDs() {
        nonScoringPSMIDs.clear();
    }


    /**
     * Adds the spectrum with the given idKey to the non scoring spectra of this
     * peptide.
     * @param idKey
     */
    public void addToNonScoringSpectra(String idKey) {
        nonScoringSpectraIDKeys.add(idKey);
    }


    /**
     * Removes the spectrum with the given idKey from the non scoring spectra of
     * this peptide.
     *
     * @param idKey
     * @return true, if the idKey was in the set
     */
    public boolean removeFromNonScoringSpectra(String idKey) {
        return nonScoringSpectraIDKeys.remove(idKey);
    }


    /**
     * Returns the set of idKeys from non scoring spectra in this peptide.
     * @return
     */
    public Set<String> getNonScoringSpectraIDKeys() {
        return nonScoringSpectraIDKeys;
    }


    /**
     * Removes all elements from the non scoring spectra set.
     */
    public void clearNonScoringSpectraIDKeys() {
        nonScoringSpectraIDKeys.clear();
    }


    /**
     * Removes the ReportPSMSet with the given key from the PSM List.
     *
     * @param psmKey
     * @return the removed PSM, or null if none is removed
     */
    public ReportPSMSet removeReportPSMSet(ReportPSMSet remSet, Map<String, Boolean> psmSetSettings) {
        allSpectraKeySet = null;
        maximalSpectraIdentificationSettings = null;
        maximalNonRedundantSpectraIdentificationSettings = null;
        Iterator<PSMReportItem> psmIter = psmList.iterator();
        String remIdKey = remSet.getIdentificationKey(psmSetSettings);

        while (psmIter.hasNext()) {
            PSMReportItem psm = psmIter.next();

            if (psm instanceof ReportPSMSet) {
                if (remIdKey.equals(((ReportPSMSet) psm).getIdentificationKey(psmSetSettings))) {
                    // the PSM is found, remove it from List and return it
                    psmIter.remove();
                    return (ReportPSMSet)psm;
                }
            }
        }

        return null;
    }


    /**
     * Gets all the identification keys of the {@link ReportPSMSet}s in this
     * peptide.
     *
     * @return
     */
    public List<String> getPSMsIdentificationKeys(Map<String, Boolean> psmSetSettings) {
        Set<String> idKeySet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            idKeySet.add(psm.getIdentificationKey(psmSetSettings));
        }

        return new ArrayList<String>(idKeySet);
    }


    /**
     * Gets all the fileNames of the PSMs.
     * @return
     */
    public List<String> getFileNames() {
        Set<String> fileNameSet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            if (psm instanceof ReportPSM) {
                fileNameSet.add(((ReportPSM) psm).getFileName());
            } else if (psm instanceof ReportPSMSet) {
                for (ReportPSM repPSM : ((ReportPSMSet) psm).getPSMs()) {
                    fileNameSet.add(repPSM.getFileName());
                }
            }
        }

        return new ArrayList<String>(fileNameSet);
    }


    /**
     * Returns a list with the PSMs of this ReportPeptide.
     * @return
     */
    public List<PSMReportItem> getPSMs() {
        return new ArrayList<PSMReportItem>(psmList);
    }


    /**
     * Gets the list of PSMs for the given psmKey (created by a PSM) from the
     * PSMs map. This should be only one, either a ReportPSM or a ReportPSMSet,
     * per peptide with the same key.
     *
     * @return
     */
    public List<PSMReportItem> getPSMsByIdentificationKey(String psmKey,
            Map<String, Boolean> psmSetSettings) {
        List<PSMReportItem> list = new ArrayList<PSMReportItem>();

        for (PSMReportItem psm : psmList) {
            if (psm.getIdentificationKey(psmSetSettings).equals(psmKey)) {
                list.add(psm);
            }
        }

        return list;
    }


    /**
     * Gets the list of PSMs for the given spectrumKey. This may be an arbitrary
     * number of PSMs or PSMSets.
     *
     * @return
     */
    public List<PSMReportItem> getPSMsBySpectrumIdentificationKey(String spectrumKey) {
        List<PSMReportItem> list = new ArrayList<PSMReportItem>();

        for (PSMReportItem psm : psmList) {
            if (psm instanceof ReportPSM) {
                if (((ReportPSM) psm).getSpectrum().getSpectrumIdentificationKey(
                        psm.getAvailableIdentificationKeySettings()).equals(spectrumKey)) {
                    list.add(psm);
                }
            } else if (psm instanceof ReportPSMSet) {

                for (ReportPSM reportPSM : ((ReportPSMSet) psm).getPSMs()) {
                    if (reportPSM.getSpectrum().getSpectrumIdentificationKey(
                            psm.getAvailableIdentificationKeySettings()).equals(spectrumKey)) {
                        list.add(psm);
                        // the PSMSet must be added only once
                        break;
                    }
                }
            }
        }

        return list;
    }


    /**
     * Returns the best score value with the given name.
     */
    @Override
    public Double getScore(String scoreName) {
        return getBestScore(scoreName);
    }


    /**
     * Returns the best score of all the PSMs for the ScoreModel given by the
     * scoreName.
     *
     * @param scoreName
     * @return
     */
    public Double getBestScore(String scoreName) {
        ScoreModel bestScoreModel = getBestScoreModel(scoreName);

        return (bestScoreModel == null) ? Double.NaN : bestScoreModel.getValue();
    }


    /**
     * Returns the ScoreModel with the best score value of all the PSMs for the
     * ScoreModel given by the scoreName.
     *
     * @param scoreName
     * @return
     */
    public ScoreModel getBestScoreModel(String scoreName) {
        ScoreModel bestScoreModel = null;

        // get the best of the scores out of the list
        for (PSMReportItem psm : psmList) {
            ScoreModel newScoreModel = null;

            if ((psm instanceof ReportPSM) &&
                    !nonScoringPSMIDs.contains(((ReportPSM)psm).getId()) &&
                    !nonScoringSpectraIDKeys.contains(((ReportPSM)psm).getSpectrum().getSpectrumIdentificationKey(psm.getAvailableIdentificationKeySettings()))) {
                newScoreModel = psm.getCompareScore(scoreName);
            } else if (psm instanceof ReportPSMSet) {
                newScoreModel =((ReportPSMSet)psm).getCompareScore(scoreName,
                        nonScoringPSMIDs, nonScoringSpectraIDKeys);
            }

            if ((newScoreModel != null) &&
                    ((bestScoreModel == null) || (newScoreModel.compareTo(bestScoreModel) < 0))) {
                bestScoreModel = newScoreModel;
            }
        }

        // no score found
        return bestScoreModel;
    }


    /**
     * Returns the score, with which the comparison will be performed.
     * For this, the ScoreModel given by the scoreShortName with the highest
     * score of this peptide will be returned.
     */
    @Override
    public ScoreModel getCompareScore(String scoreShortname) {
        return getBestScoreModel(scoreShortname);
    }


    /**
     * Returns a List of all the {@link Modification}s occurring in the PSMs.
     *
     * @return
     */
    public List<Modification> getModificationsList() {
        List<Modification> modList = new ArrayList<Modification>();

        for (PSMReportItem psm : psmList) {
            for(Map.Entry<Integer, Modification> modIt : psm.getModifications().entrySet()) {
                modList.add(modIt.getValue());
            }
        }

        return modList;
    }


    /**
     * Getter for the modifications.<br/>
     * Returns the modifications of the first PSM.<br/>
     * WARNING: this makes no sense, if the considerModifications is false in
     * the {@link PeptideViewer}.
     *
     * @return
     */
    public Map<Integer, Modification> getModifications() {
        for (PSMReportItem psm : psmList) {
            return psm.getModifications();
        }

        return new HashMap<Integer, Modification>(1);
    }



    /**
     * Generates the idString for a peptide for the given PSM.<br/>
     * This takes the considerModifications into account and calls
     * {@link ReportPeptide#getIdString(String, Map, boolean)}
     *
     * @param psm
     * @return
     */
    public static String createStringID(PSMReportItem psm, boolean considerModifications) {
        return psm.getPeptideStringID(considerModifications);
    }


    /**
     * Returns a set containing the IDs of all the used Spectra, not only the
     * scoring spectra.
     *
     * @return
     */
    public List<String> getSpectraIdentificationKeys() {
        if (allSpectraKeySet == null) {
            allSpectraKeySet = new HashSet<String>(
                    getSpectraIdentificationKeys(getNotRedundantIdentificationKeySettings()));
        }

        return new ArrayList<String>(allSpectraKeySet);
    }


    /**
     * Returns a set containing the IDs of all the used Spectra, not only the
     * scoring spectra, given the maximal IdentificationKeySet.
     *
     * @return
     */
    public List<String> getSpectraIdentificationKeys(Map<String, Boolean> maximalKeySettings) {
        Set<String> SpectraKeySet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            if (psm instanceof ReportPSM) {
                SpectraKeySet.add(((ReportPSM) psm).getSpectrum().
                        getSpectrumIdentificationKey(maximalKeySettings));
            } else if (psm instanceof ReportPSMSet) {
                for (ReportPSM reportPSM : ((ReportPSMSet) psm).getPSMs()) {
                    SpectraKeySet.add(reportPSM.getSpectrum().
                            getSpectrumIdentificationKey(maximalKeySettings));
                }
            }
        }

        return new ArrayList<String>(SpectraKeySet);
    }


    /**
     * Returns the settings, which are available for identification key
     * calculation, i.e. the {@link IdentificationKeySettings} which are
     * available on all spectra in this peptide.
     * @return
     */
    public Map<String, Boolean> getAvailableIdentificationKeySettings() {
        if (maximalSpectraIdentificationSettings == null) {
            // initialise the map with maximal possible values
            maximalSpectraIdentificationSettings = new HashMap<String, Boolean>(5);
            maximalSpectraIdentificationSettings.put(
                    IdentificationKeySettings.MASSTOCHARGE.name(), true);
            maximalSpectraIdentificationSettings.put(
                    IdentificationKeySettings.RETENTION_TIME.name(), true);
            maximalSpectraIdentificationSettings.put(
                    IdentificationKeySettings.SOURCE_ID.name(), true);
            maximalSpectraIdentificationSettings.put(
                    IdentificationKeySettings.SPECTRUM_TITLE.name(), true);
            maximalSpectraIdentificationSettings.put(
                    IdentificationKeySettings.CHARGE.name(), true);

            // adjust the maximal available PSM set settings
            for (PSMReportItem psm : psmList) {
                Map<String, Boolean> psmAvailables = psm.getAvailableIdentificationKeySettings();

                Set<String> setAvailables = new HashSet<String>(maximalSpectraIdentificationSettings.keySet());

                for (String setting : setAvailables) {
                    if (!psmAvailables.containsKey(setting) || !psmAvailables.get(setting) ) {
                        maximalSpectraIdentificationSettings.remove(setting);
                    }
                }
            }

            maximalNonRedundantSpectraIdentificationSettings = null;
        }

        return maximalSpectraIdentificationSettings;
    }


    /**
     * Returns the settings, which are available for identification key
     * calculation, i.e. the {@link IdentificationKeySettings} which are
     * available on all spectra in this peptide and are not redundant, i.e. the
     * best minimal set of settings.
     * @return
     */
    public Map<String, Boolean> getNotRedundantIdentificationKeySettings() {
        if (maximalNonRedundantSpectraIdentificationSettings == null) {
            maximalNonRedundantSpectraIdentificationSettings =
                    IdentificationKeySettings.noRedundantSettings(getAvailableIdentificationKeySettings());
        }
        return maximalNonRedundantSpectraIdentificationSettings;
    }


    /**
     * Getter for the number of spectra (not only scoring, but all).
     * @return
     */
    public Integer getNrSpectra() {
        return getSpectraIdentificationKeys().size();
    }


    /**
     * returns a set containing the ID keys of the scoring Spectra.
     * @return
     */
    public List<String> getScoringSpectraIdentificationKeys() {
        Set<String> scoringSpectraKeySet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            if (psm instanceof ReportPSM) {
                if (!nonScoringPSMIDs.contains(((ReportPSM) psm).getId())) {
                    scoringSpectraKeySet.add(((ReportPSM) psm).getSpectrum().
                            getSpectrumIdentificationKey(getNotRedundantIdentificationKeySettings()));
                }
            } else if (psm instanceof ReportPSMSet) {
                for (ReportPSM reportPSM : ((ReportPSMSet) psm).getPSMs()) {
                    if (!nonScoringPSMIDs.contains(reportPSM.getId())) {
                        scoringSpectraKeySet.add(reportPSM.getSpectrum().
                                getSpectrumIdentificationKey(getNotRedundantIdentificationKeySettings()));
                    }
                }
            }
        }

        // now remove the ID keys from the non scoring set
        scoringSpectraKeySet.removeAll(nonScoringSpectraIDKeys);
        return new ArrayList<String>(scoringSpectraKeySet);
    }


    /**
     * Getter for the number of PSMs.
     * @return
     */
    public Integer getNrPSMs() {
        // the psmList should be consistent with the number of PSMs, as the
        // peptides are rebuild, if the psmSetSettings are changed
        return psmList.size();
    }


    /**
     * Returns a List of all the sourceIDs in this peptide.
     * @return
     */
    public List<String> getSourceIDs() {
        Set<String> sourcesSet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            if (psm.getSourceID() != null) {
                sourcesSet.add(psm.getSourceID());
            }
        }

        return new ArrayList<String>(sourcesSet);
    }


    /**
     * Returns a List of all the spectrum titles in this peptide.
     * @return
     */
    public List<String> getSpectrumTitles() {
        Set<String> titlesSet = new HashSet<String>();

        for (PSMReportItem psm : psmList) {
            if (psm.getSpectrumTitle() != null) {
                titlesSet.add(psm.getSpectrumTitle());
            }
        }

        return new ArrayList<String>(titlesSet);
    }


    /**
     * Returns a nice name / header for the spectrum in the PSM or PSM set
     * specified by the spectrumKey.
     *
     * @param spectrumKey
     * @return
     */
    public String getNiceSpectrumName(String spectrumKey) {
        String name = null;
        List<PSMReportItem> psms =
                getPSMsBySpectrumIdentificationKey(spectrumKey);
        for (PSMReportItem psm : psms) {
            name = psm.getNiceSpectrumName();
        }

        if ((psms.size() > 1) && (name != null)) {
            name += " [" + psms.size() + "]";
        }

        return name;
    }


    @Override
    public boolean getIsDecoy() {
        return isDecoy;
    }


    /**
     * Setter for isDecoy
     * @param isDecoy
     */
    public void setIsDecoy(boolean isDecoy) {
        this.isDecoy = isDecoy;
    }


    @Override
    public void setFDRScore(Double score) {
        if (fdrScore != null) {
            fdrScore.setValue(score);
        } else {
            fdrScore = new ScoreModel(score,
                    ScoreModelEnum.PEPTIDE_LEVEL_FDR_SCORE);
        }
    }


    @Override
    public ScoreModel getFDRScore() {
        return fdrScore;
    }


    @Override
    public double getFDR() {
        if (fdrValue == null) {
            return Double.NaN;
        } else {
            return fdrValue;
        }
    }


    @Override
    public void setFDR(double fdr) {
        this.fdrValue = fdr;
    }


    @Override
    public double getQValue() {
        if (qValue == null) {
            return Double.NaN;
        } else {
            return qValue;
        }
    }


    @Override
    public void setQValue(double value) {
        this.qValue = value;
    }


    @Override
    public void dumpFDRCalculation() {
        isFDRGood = false;
        qValue = null;
        fdrScore = null;
        fdrValue = Double.POSITIVE_INFINITY;
    }


    @Override
    public void updateDecoyStatus(DecoyStrategy strategy, Pattern p) {
        switch (strategy) {
        case ACCESSIONPATTERN:
            this.isDecoy = isDecoyWithPattern(p);
            break;

        case SEARCHENGINE:
            List<ReportPSM> psms = new ArrayList<ReportPSM>();
            for (PSMReportItem psm : getPSMs()) {
                if (psm instanceof ReportPSM) {
                    psms.add((ReportPSM)psm);
                } else if (psm instanceof ReportPSMSet) {
                    psms.addAll(((ReportPSMSet) psm).getPSMs());
                }
            }

            this.isDecoy = true;
            for (ReportPSM psm : psms) {
                if ((psm.getSpectrum().getIsDecoy() == null) ||
                        !psm.getSpectrum().getIsDecoy()) {
                    // not a decoy spectrum, so the peptide is not a decoy
                    this.isDecoy = false;
                    break;
                }
            }
            break;

        default:
            this.isDecoy = false;
        }
    }


    /**
     * Returns true, if the peptide is a decoy with the given pattern for decoys
     * used on the accessions.
     *
     * @param p Pattern for decoy identification used on the accession
     */
    private boolean isDecoyWithPattern(Pattern p) {
        Matcher m;

        for (Accession acc : getAccessions()) {
            m = p.matcher(acc.getAccession());
            if (!m.matches()) {
                // not a decoy accession, so the peptide is not a decoy
                return false;
            }
        }

        // no target in the accessions -> it's a decoy
        return true;
    }


    /**
     * Getter for isFDRGood
     * @return
     */
    public boolean getIsFDRGood() {
        return isFDRGood;
    }


    @Override
    public void setIsFDRGood(boolean isGood) {
        isFDRGood = isGood;
    }
}
