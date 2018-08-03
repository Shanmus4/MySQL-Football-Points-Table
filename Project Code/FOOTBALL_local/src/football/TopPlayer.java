/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package football;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 *
 * @author SHANMU
 */
@Entity
@Table(name = "top_player", catalog = "football", schema = "")
@NamedQueries({
    @NamedQuery(name = "TopPlayer.findAll", query = "SELECT t FROM TopPlayer t"),
    @NamedQuery(name = "TopPlayer.findByPlayer", query = "SELECT t FROM TopPlayer t WHERE t.player = :player"),
    @NamedQuery(name = "TopPlayer.findByGoals", query = "SELECT t FROM TopPlayer t WHERE t.goals = :goals"),
    @NamedQuery(name = "TopPlayer.findByAssists", query = "SELECT t FROM TopPlayer t WHERE t.assists = :assists"),
    @NamedQuery(name = "TopPlayer.findByPass", query = "SELECT t FROM TopPlayer t WHERE t.pass = :pass"),
    @NamedQuery(name = "TopPlayer.findByAchievements", query = "SELECT t FROM TopPlayer t WHERE t.achievements = :achievements")})
public class TopPlayer implements Serializable {
    @Transient
    private PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "Player")
    private String player;
    @Column(name = "Goals")
    private Integer goals;
    @Column(name = "Assists")
    private Integer assists;
    @Column(name = "Pass")
    private Integer pass;
    @Column(name = "Achievements")
    private String achievements;

    public TopPlayer() {
    }

    public TopPlayer(String player) {
        this.player = player;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        String oldPlayer = this.player;
        this.player = player;
        changeSupport.firePropertyChange("player", oldPlayer, player);
    }

    public Integer getGoals() {
        return goals;
    }

    public void setGoals(Integer goals) {
        Integer oldGoals = this.goals;
        this.goals = goals;
        changeSupport.firePropertyChange("goals", oldGoals, goals);
    }

    public Integer getAssists() {
        return assists;
    }

    public void setAssists(Integer assists) {
        Integer oldAssists = this.assists;
        this.assists = assists;
        changeSupport.firePropertyChange("assists", oldAssists, assists);
    }

    public Integer getPass() {
        return pass;
    }

    public void setPass(Integer pass) {
        Integer oldPass = this.pass;
        this.pass = pass;
        changeSupport.firePropertyChange("pass", oldPass, pass);
    }

    public String getAchievements() {
        return achievements;
    }

    public void setAchievements(String achievements) {
        String oldAchievements = this.achievements;
        this.achievements = achievements;
        changeSupport.firePropertyChange("achievements", oldAchievements, achievements);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (player != null ? player.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof TopPlayer)) {
            return false;
        }
        TopPlayer other = (TopPlayer) object;
        if ((this.player == null && other.player != null) || (this.player != null && !this.player.equals(other.player))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "football.TopPlayer[player=" + player + "]";
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

}
