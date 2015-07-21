/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package se.kth.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

/**
 *
 * @author fabriziodemaria
 */

public final class MembershipList {
    
    private static final Logger log = LoggerFactory.getLogger(MembershipList.class);
    
    
    private final NatedAddress selfAddress;
    private Integer selfIncarnation;
    private final HashMap<NatedAddress,MemberInfo> neighboursNodes = new HashMap<NatedAddress,MemberInfo>();
    private HashMap<Integer,HashSet> tabuMap = new HashMap<Integer,HashSet>();
    
    
    private Random rand = new Random();
    
    public MembershipList(Set<NatedAddress> addresses, NatedAddress selfAddress){
        
        this.selfAddress=selfAddress;
        for(NatedAddress na : addresses){
            add(na,0);
        }
        selfIncarnation = 0;
    }
    
    
    
    
    //ADD
    public boolean add(NatedAddress n, Integer incarnationNumber){
        
        NatedAddress found = containsAddress(n);
        
        if(n.getId().equals(selfAddress.getId())) return false;
        
        if(found!=null){
            MemberInfo mi = neighboursNodes.get(found);
            if(mi.dead==true && mi.incarnationNumber<incarnationNumber){
                neighboursNodes.remove(found);
                
                //Update the parent list of the new nodewith the last ones in the cache
                //Set<NatedAddress> parentList = new HashSet<NatedAddress>(found.getParents());
                NatedAddress toAdd = deepCopy(n);
                //toAdd.getParents().clear();
                //toAdd.getParents().addAll(parentList);
                
                neighboursNodes.put(toAdd,new MemberInfo(false,false,incarnationNumber, n.getId()));
                log.info("NODEADDED {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), n.getId(), n.getParents()});
                return true;
            }
            return false;
        } else {
            neighboursNodes.put(deepCopy(n),new MemberInfo(false,false,incarnationNumber, n.getId()));
            log.info("NODEADDED {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), n.getId(), n.getParents()});
            return true;
        }
    }
    
    
    private NatedAddress containsAddress(NatedAddress toFind){
        NatedAddress found = null;
        for(NatedAddress na : neighboursNodes.keySet()){
            if(na.getId().equals(toFind.getId())){
                found = na;
            }
        }
        return found;
    }
    
    
    //REMOVE
    public boolean remove(NatedAddress n, Integer incarnationNumber){
        
        NatedAddress found = containsAddress(n);
        
        if(found!=null){
            MemberInfo mi = neighboursNodes.get(found);
            if(mi.dead==false && mi.incarnationNumber<=incarnationNumber){
                neighboursNodes.remove(found);
                neighboursNodes.put(deepCopy(n),new MemberInfo(false,true,incarnationNumber, n.getId()));
                log.info("NODEREMOVE {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), n.getId(), n.getParents()});
                return true;
            }
            return false;
        }
        return false;
    }
    
    
    //SUSPECT
    public boolean suspectNode(NatedAddress n, Integer incarnationValue){
        NatedAddress found = containsAddress(n);
        
        if(found!=null){
            if(neighboursNodes.get(found).dead==false && neighboursNodes.get(found).suspected==false &&  neighboursNodes.get(found).incarnationNumber<incarnationValue){
                neighboursNodes.get(found).setSuspected(true);
                neighboursNodes.get(found).setIncarnationNumber(incarnationValue);
                return true;
            }
            return false;
        }
        return false;
    }
    
    
    //UNSUSPECT
    public boolean unsuspectNode(NatedAddress n, Integer incarnationValue){
        NatedAddress found = containsAddress(n);
        if(found!=null){
            if(neighboursNodes.get(found).dead==false &&  neighboursNodes.get(found).suspected==true && neighboursNodes.get(found).incarnationNumber<incarnationValue){
                neighboursNodes.get(found).setSuspected(false);
                neighboursNodes.get(found).setIncarnationNumber(incarnationValue);
                return true;
            }
            return false;
        }
        return false;
    }
    
    
    
    
    
    
    
    /*
    
    SUPPORTING CODE
    
    */
    
    public boolean contains(NatedAddress na){
        
        NatedAddress found = containsAddress(na);
        if(found!=null){
            if(neighboursNodes.get(found).dead==false){
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
    
    public boolean isSuspected(NatedAddress n){
        NatedAddress found = containsAddress(n);
        
        if(found==null) {
            return false;
        } else if (neighboursNodes.get(found).dead==true) {
            return false;
        }
        
        return neighboursNodes.get(found).getSuspected();
    }
    
    public boolean isEmpty() {
        return (size()==0);
    }
    
    public int size() {
        int i = 0;
        for(NatedAddress na : neighboursNodes.keySet()){
            if(neighboursNodes.get(na).dead==false){
                i++;
            }
        }
        return i;
    }
    
    public NatedAddress randomNode() {
        if(isEmpty()) return null;
        while(true){
            int randomNum = rand.nextInt(((neighboursNodes.size()-1) - 0) + 1) + 0;
            NatedAddress node = (NatedAddress) neighboursNodes.keySet().toArray()[randomNum];
            if(neighboursNodes.get(node).dead==false){
                return node;
            }
        }
        
    }
    
    //Return alive nodes in the list
    public ArrayList<NatedAddress> getNeighboursList(){
        
        ArrayList<NatedAddress> list = new ArrayList<NatedAddress>();
        for(NatedAddress na : neighboursNodes.keySet()){
            if(neighboursNodes.get(na).dead==false)
                list.add(na);
        }
        return list;
    }
    
    public void printNeighbour() {
        //log.info("NNN " + neighboursNodes.size() );
        if(SwimComp.printListOfNeighbours){
            System.out.print(" " + selfAddress.getId() + " xxxx TOTSIZE: " + getNeighboursList().size() + " --> ");
            
            for(NatedAddress na : this.getNeighboursList()){
                System.out.print(" - " + na.getId());
            }
            System.out.println();
        }
    }
    
    void selectRandom(ArrayList<NatedAddress> indirectPings, Integer K_VALUE) throws Exception {
        
        if(K_VALUE>size()) throw new Exception("Cannot chose K random nodes");
        
        for(int k = 0; k < K_VALUE; k++){
            while(true){
                NatedAddress tmp = randomNode();
                if(!indirectPings.contains(tmp) && neighboursNodes.get(tmp).dead==false){
                    indirectPings.add(tmp);
                    break;
                }
            }
            
        }
    }
    
    
    
    /**
     * @return the selfIncarnation
     */
    public Integer getSelfIncarnation() {
        return selfIncarnation;
    }
    
    /**
     * @param selfIncarnation the selfIncarnation to set
     */
    public void setSelfIncarnation(Integer selfIncarnation) {
        this.selfIncarnation = selfIncarnation;
    }
    
    Integer getIncarnationForMember(NatedAddress address) {
        if(neighboursNodes.containsKey(address)){
            return (neighboursNodes.get(address).incarnationNumber);
        }
        return -1;
    }
    
    
   //This method tries to add a new parent for a certain nated node in the membershipList 
    boolean updateNewParents(NatedAddress infoTarget, NatedAddress newParent) {
        
        if(tabuMap.containsKey(infoTarget.getId())){
            if(tabuMap.get(infoTarget.getId()).contains(newParent.getId())){
                //I added this parent before, not allowed until clearing of the cacheN
                NatedAddress print = null;
                for(NatedAddress na : neighboursNodes.keySet()){
                    if(na.getId().equals(infoTarget.getId())){
                        print = na;
                    }
                }
                if(print!=null)
                    log.info("FORBIDDEN {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), infoTarget.getId(), print.getParents()});
                return false;
            }
        }
        
        NatedAddress toUpdate = null;
        
        for(NatedAddress na : neighboursNodes.keySet()){
            if(na.getId().equals(infoTarget.getId())){
                for(NatedAddress na2 : na.getParents()){
                    if(na2.getId().equals(newParent.getId())){
                        return false;
                    }
                }
                //I have the target but not the new parent
                toUpdate=na;
                break;
            }
        }
        
        if(toUpdate!=null){
            MemberInfo mi = neighboursNodes.get(toUpdate);
            NatedAddress toAdd = deepCopy(toUpdate,newParent);
            neighboursNodes.remove(toUpdate);
            neighboursNodes.put(toAdd, mi);
            log.info("ADDEDP {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), toAdd.getId(), toAdd.getParents()});
            
            if(!tabuMap.containsKey(toAdd.getId())){
                log.info("UPDATEDPN {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), toAdd.getId(), toAdd.getParents()});
                tabuMap.put(toAdd.getId(), new HashSet<Integer>(newParent.getId()));
            } else {
                log.info("UPDATEDP {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), toAdd.getId(), toAdd.getParents()});
                tabuMap.get(toAdd.getId()).add(newParent.getId());
            }
            
            
            return true;
        }
        return false;
    }
    
    
       //This method tries to delete a parent for a certain nated node in the membershipList 
    boolean updateDeadParents(NatedAddress infoTarget, NatedAddress newParent) {
        
        NatedAddress toUpdate = null;
        NatedAddress toRemove = null;
        
        for(NatedAddress na : neighboursNodes.keySet()){
            if(na.getId().equals(infoTarget.getId())){
                //I have the target for the information
                toUpdate=na;
                for(NatedAddress na2 : toUpdate.getParents()){
                    if(na2.getId().equals(newParent.getId())){
                        //I have to remove this from toUpdate
                        toRemove=na2;
                        break;
                    }
                }
            }
        }
        
        if(toRemove!=null && toUpdate!=null){
            MemberInfo mi = neighboursNodes.get(toUpdate);
            NatedAddress toAdd = deepCopyRemove(toUpdate,toRemove);
            neighboursNodes.remove(toUpdate);
            neighboursNodes.put(toAdd, mi);
            log.info("REMOVEDP {} parents for nated node {}: {}", new Object[]{selfAddress.getId(), toAdd.getId(), toAdd.getParents()});
            return true;
        }
        return false;
    }
    
    
    
    private static class MemberInfo {
        private boolean suspected;
        private boolean dead;
        private Integer incarnationNumber;
        private Integer nodeID;
        
        public MemberInfo(boolean suspected, boolean dead, Integer incarnationNumber, Integer nodeID) {
            this.suspected=suspected;
            this.incarnationNumber=incarnationNumber;
            this.nodeID = nodeID;
            this.dead = dead;
        }
        
        /**
         * @return the suspected
         */
        public boolean isSuspected() {
            return suspected;
        }
        
        /**
         * @param suspected the suspected to set
         */
        public void setSuspected(boolean suspected) {
            this.suspected = suspected;
        }
        
        /**
         * @return the incarnationNumber
         */
        public Integer getIncarnationNumber() {
            return incarnationNumber;
        }
        
        /**
         * @param incarnationNumber the incarnationNumber to set
         */
        public void setIncarnationNumber(Integer incarnationNumber) {
            this.incarnationNumber = incarnationNumber;
        }
        
        /**
         * @return the nodeID
         */
        public Integer getNodeID() {
            return nodeID;
        }
        
        /**
         * @param nodeID the nodeID to set
         */
        public void setNodeID(Integer nodeID) {
            this.nodeID = nodeID;
        }
        
        private boolean getSuspected() {
            return this.suspected;
        }
        
        /**
         * @return the dead
         */
        public boolean isDead() {
            return dead;
        }
        
        /**
         * @param dead the dead to set
         */
        public void setDead(boolean dead) {
            this.dead = dead;
        }
        
        
        
    }
    
    private NatedAddress deepCopy(NatedAddress na){
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(na.getIp(), na.getPort(), na.getId()), na.getNatType(), new HashSet<NatedAddress>(na.getParents()));
        return na2;
    }
    
    private NatedAddress deepCopy(NatedAddress self, NatedAddress par){
        Set<NatedAddress> parentListTmp = new HashSet<NatedAddress>(self.getParents());
        parentListTmp.add(deepCopy(par));
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(self.getIp(), self.getPort(), self.getId()), self.getNatType(), parentListTmp);
        return na2;
    }
    
    
    private NatedAddress deepCopyRemove(NatedAddress self, NatedAddress par){
        Set<NatedAddress> parentListTmp = new HashSet<NatedAddress>(self.getParents());
        parentListTmp.remove(par);
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(self.getIp(), self.getPort(), self.getId()), self.getNatType(), parentListTmp);
        return na2;
    }
    
    
}
