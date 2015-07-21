/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package se.kth.swim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.net.InfoPiggyback;
import se.kth.swim.msg.net.InfoType;

/**
 *
 * @author samia
 */
public class PiggybackList {
    
    private static final Logger log = LoggerFactory.getLogger(MembershipList.class);
    
    private Map<InfoPiggyback, Integer> updates;
    private final int maxSize;
    
    public PiggybackList(HashMap<InfoPiggyback, Integer> m, int maxSize) {
        updates = new HashMap<InfoPiggyback, Integer>();
        updates = m;
        this.maxSize = maxSize;
        
    }
    
    public PiggybackList(int maxSize) {
        updates = new HashMap<InfoPiggyback, Integer>();
        this.maxSize = maxSize;
    }
    
    public void add(InfoPiggyback info, Integer counter) {
        
        
        if (updates.size() >= maxSize) {
            deleteItem();
        }
        updates.put(info, counter);
        
    }
    
    public void remove(InfoPiggyback info) {
        if (updates.containsKey(info)) {
            updates.remove(info);
        }
    }
    
    public int listSize() {
        return updates.size();
    }
    
    private void deleteItem() {
        int min = 10000;
        InfoPiggyback tmp = null;
        for (InfoPiggyback i : updates.keySet()) {
            if (updates.get(i) <= min) {
                min = updates.get(i);
                tmp = i;
            }
        }
        
        if (tmp != null) {
            updates.remove(tmp);
        }
    }
    
    public ArrayList<InfoPiggyback> getList(){
        return new ArrayList<InfoPiggyback>(updates.keySet());
    }
    
    public void decreaseCounters() {
        //decrease counters and remove 0
        Collection<InfoPiggyback> coll = new LinkedList<InfoPiggyback>();
        for(InfoPiggyback ipb : updates.keySet()){
            coll.add(ipb);
        }
        for(InfoPiggyback ipb : coll){
            int newCounter = updates.get(ipb);
            newCounter--;
            if(newCounter==0){
                updates.remove(ipb);
            } else {
                updates.put(ipb, newCounter);
            }
        }
    }

    Integer getDissemination(InfoPiggyback ipb) {
        return updates.get(ipb);
    }
    
}
