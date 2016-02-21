package de.mpc.pia.intermediate;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class Group implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** ID of the group */
	private long ID;
	/** ID of the group's tree */
	private long treeID;
	/** List of the direct peptides in the group. */
	private Map<String, Peptide> peptides;
	/** Children groups of this group, i.e. groups where this group points to. */
	private Map<Long, Group> children;
	/** Parents of this group, i.e. groups pointing to this group. */
	private Map<Long, Group> parents;
	/** List of the direct accessions of this group. */
	private Map<String, Accession> accessions;
	/** List of all parents' and own accession. */
	private Map<String, Accession> allAccessions;
	
	
	/**
	 * Basic Constructor, sets all the maps to null and score to NaN.
	 * 
	 * @param id
	 */
	public Group(long id) {
		this.ID = id;
		this.treeID = -1;
		this.peptides = null;
		this.children = new HashMap<Long, Group>();
		this.parents = new HashMap<Long, Group>();
		this.accessions = new HashMap<String, Accession>();
		this.allAccessions = new HashMap<String, Accession>();
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if ( !(obj instanceof Group) ) {
			return false;
		}
		
		Group objGroup = (Group)obj;
	    if ((objGroup.ID == this.ID) &&
	    		(objGroup.treeID == this.treeID) &&
	    		(((peptides != null) && peptides.equals(objGroup.peptides)) ||
	    				((peptides == null) && (objGroup.peptides == null))) &&
	    		objGroup.children.equals(children) &&
	    		objGroup.parents.equals(parents) &&
	    		objGroup.accessions.equals(accessions) &&
	    		objGroup.allAccessions.equals(allAccessions)) {
	    	return true;
	    } else {
	    	return false;
	    }
	}
	
	
	@Override
	public int hashCode() {
		int hash = 0;
		
		hash += (new Long(ID)).hashCode();
		hash += (new Long(treeID)).hashCode();
		
		hash += (peptides != null) ? peptides.hashCode() : 0;
		
		// we can't take the children's hashcode directly, as the group is referenced as parent
		if (children != null) {
			for (Map.Entry<Long, Group> gIt : children.entrySet()) {
				hash += (new Long(gIt.getValue().getID())).hashCode();
			}
		}
		
		// we can't take the parents' hashcode directly, as the group is referenced as child
		if (parents != null) {
			for (Map.Entry<Long, Group> gIt : parents.entrySet()) {
				hash += (new Long(gIt.getValue().getID())).hashCode();
			}
		}
		
		hash += (accessions != null) ? accessions.hashCode() : 0;
		hash += (allAccessions != null) ? allAccessions.hashCode() : 0;
		
		return hash;
	}
	
	
	/**
	 * Getter for the ID.
	 * 
	 * @return
	 */
	public long getID() {
		return this.ID;
	}
	
	
	/**
	 * Setter for the treeID.
	 * 
	 * @param id
	 */
	public void setTreeID(long id) {
		this.treeID = id;
	}
	
	
	/**
	 * Getter for the group's treeID.
	 * 
	 * @return
	 */
	public long getTreeID() {
		return treeID;
	}
	
	
	/**
	 * Setter for the peptides.
	 * 
	 * @param peptides
	 */
	public void setPeptides(Map<String, Peptide> peptides) {
		this.peptides = peptides;
	}
	
	
	/**
	 * Adds a single peptide to the group.
	 * 
	 * @param peptides
	 */
	public void addPeptide(Peptide peptide) {
		if (peptides == null) {
			peptides = new HashMap<String, Peptide>();
		}
		
		peptides.put(peptide.getSequence(), peptide);
	}
	
	
	/**
	 * Getter for the peptides.
	 * 
	 * @return
	 */
	public Map<String, Peptide> getPeptides() {
		if (peptides == null) {
			peptides = new HashMap<String, Peptide>();
		}
		
		return peptides;
	}
	
	
	/**
	 * getter for all peptides, including children's peptides.
	 * @return
	 */
	public Map<String, Peptide> getAllPeptides() {
		Map<String, Peptide> ret = new HashMap<String, Peptide>();
		
		if (peptides != null) {
			for (Map.Entry<String, Peptide> pep : peptides.entrySet()) {
				ret.put(pep.getKey(), pep.getValue());
			}
		}
		
		for (Map.Entry<Long, Group> child : getAllPeptideChildren().entrySet()) {
			Map<String, Peptide> childPepMap = child.getValue().getPeptides();
			if (childPepMap != null) {
				for (Map.Entry<String, Peptide> childPeps : childPepMap.entrySet()) {
					ret.put(childPeps.getKey(), childPeps.getValue());
				}
			}
		}
		
		return ret;
	}
	
	/**
	 * Adds a child to the children map.
	 * If the map is not yet initialized, initialize it.
	 * 
	 * @param peptides
	 */
	public void addChild(Group child) {
		children.put(child.getID(), child);
		
		if (allAccessions != null) {
			for (Map.Entry<String, Accession> acc : allAccessions.entrySet()) {
				child.addToAllAccessions(acc.getValue());
			}
		}
	}
	
	
	/**
	 * Getter for the children.
	 * 
	 * @return
	 */
	public Map<Long, Group> getChildren() {
		return children;
	}
	
	
	/**
	 * Getter for all children groups of this group, including children's
	 * children and so on.
	 */
	public Map<Long, Group> getAllChildren(){
		Map<Long, Group> allChildren = new HashMap<Long, Group>();
		
		for (Map.Entry<Long, Group> cIt : children.entrySet()) {
			allChildren.put(cIt.getKey(), cIt.getValue());
			
			Map<Long, Group> childChildren = cIt.getValue().getAllChildren();
			for (Map.Entry<Long, Group> ccIt : childChildren.entrySet()) {
				allChildren.put(ccIt.getKey(), ccIt.getValue());
			}
		}
		
		return allChildren;
	}
	
	
	/**
	 * Getter for all children groups of this group that have at least one
	 * peptide, recursive, i.e. get the reporting peptide groups.
	 */
	public Map<Long, Group> getAllPeptideChildren(){
		Map<Long, Group> allChildren = new HashMap<Long, Group>();
		Map<Long, Group> childChildren;
		
		for (Map.Entry<Long, Group> cIt : children.entrySet()) {
			childChildren = cIt.getValue().getAllPeptideChildren();
			
			for (Map.Entry<Long, Group> ccIt : childChildren.entrySet()) {
				allChildren.put(ccIt.getKey(), ccIt.getValue());
			}
			
			if ((cIt.getValue().getPeptides() != null) &&
					(cIt.getValue().getPeptides().size() > 0)) {
				allChildren.put(cIt.getKey(), cIt.getValue());
			}
		}
		
		return allChildren;
	}
	
	
	/**
	 * Adds a new group to the map of parents.
	 * If the map is not yet initialized, initialize it.
	 * 
	 * @param parent
	 */
	public void addParent(Group parent) {
		parents.put(parent.getID(), parent);
		if (parent.getAllAccessions() != null) {
			for (Map.Entry<String, Accession> acc : parent.getAllAccessions().entrySet()) {
				addToAllAccessions(acc.getValue());
			}
		}
	}
	
	
	/**
	 * Getter for the parents.
	 * 
	 * @return
	 */
	public  Map<Long, Group> getParents() {
		return parents;
	}
	
	
	/**
	 * Adds a new accession to the map of accessions.
	 * If the map is not yet initialized, initialize it.
	 * 
	 * @param peptides
	 */
	public void addAccession(Accession accession) {
		accessions.put(accession.getAccession(), accession);
		addToAllAccessions(accession);
	}
	
	
	/**
	 * Getter for the accessions.
	 * 
	 * @return
	 */
	public Map<String, Accession> getAccessions() {
		return accessions;
	}
	
	
	/**
	 * Adds the given accession to the map of all accessions and also dates up
	 * the children.
	 * 
	 * @param accession
	 */
	protected void addToAllAccessions(Accession accession) {
		allAccessions.put(accession.getAccession(), accession);
		
		for (Map.Entry<Long, Group> child : children.entrySet()) {
			child.getValue().addToAllAccessions(accession);
		}
	}
	
	
	/**
	 * Getter for the accessions of this group and all the parents.
	 * 
	 * @return
	 */
	public Map<String, Accession> getAllAccessions() {
		return allAccessions;
	}
	
	
	/**
	 * String getter for the accessions.
	 * 
	 * @return
	 */
	public String getAccessionsStr() {
		StringBuffer sb = new StringBuffer();
		
		if (accessions != null) {
			for (Map.Entry<String, Accession> acc : accessions.entrySet()) {
				sb.append(acc.getKey() + " ");
			}
		}
		
		return sb.toString();
	}
	
	
	/**
	 * Returns the accessions as an array of strings.
	 *  
	 * @return
	 */
	public String[] getAccessionsStrArray() {
		String[] accessionsArr = null;
		int i = 0;
		
		if (accessions != null) {
			accessionsArr = new String[accessions.size()];
			for (Map.Entry<String, Accession> acc : accessions.entrySet()) {
				accessionsArr[i] = new String(acc.getKey());
				i++;
			}
		}
		
		return accessionsArr;
	}
	
	
	/**
	 * String getter for the peptides.
	 * 
	 * @return
	 */
	public String getPeptidesStr() {
		StringBuffer sb = new StringBuffer();
		
		if (peptides != null) {
			for (Map.Entry<String, Peptide> pep : peptides.entrySet()) {
				sb.append(pep.getKey() + " ");
			}
		}
		
		for (Map.Entry<Long, Group> pepChild : getAllPeptideChildren().entrySet()) {
			sb.append(pepChild.getValue().getPeptidesStr());
		}
		
		return sb.toString();
	}
	
	
	/**
	 * Adds the given offset to the own id and and the keys in the children and
	 * parent maps.<br/>
	 * This function should only be called, if all the IDs in a cluster are
	 * updated.
	 * 
	 * @param offset
	 */
	public void setOffset(Long offset) {
		Map<Long, Group> tmpMap;
		this.ID += offset;
		
		// offset the children keys
		tmpMap = new HashMap<Long, Group>(children.size());
		for (Map.Entry<Long, Group> childrenIt : children.entrySet()) {
			tmpMap.put( childrenIt.getKey()+offset, childrenIt.getValue());
		}
		children = tmpMap;
		
		// offset the parents' keys
		tmpMap = new HashMap<Long, Group>(parents.size());
		for (Map.Entry<Long, Group> parentsIt : parents.entrySet()) {
			tmpMap.put( parentsIt.getKey()+offset, parentsIt.getValue());
		}
		parents = tmpMap;
	}
	
	
	/**
	 * Removes the given accession pointer from the direct accessions map and
	 * dates up the all accessions map.
	 * 
	 * @param accession
	 */
	public void removeAccession(Accession accession) {
		accessions.remove(accession.getAccession());
		removeFromAllAccessions(accession);
	}
	
	
	/**
	 * Removes the given accession from the map of all accessions and also dates
	 * up the children.
	 * 
	 * @param accession
	 */
	private void removeFromAllAccessions(Accession accession) {
		allAccessions.remove(accession.getAccession());
		
		for (Group child : children.values()) {
			child.removeFromAllAccessions(accession);
		}
	}
}