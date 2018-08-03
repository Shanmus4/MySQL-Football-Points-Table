/*
 * FOOTBALLView.java
 */

package football;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import org.jdesktop.application.Task;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.RollbackException;
import javax.swing.*;
import java.sql.*;
import net.proteanit.sql.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import org.jdesktop.beansbinding.AbstractBindingListener;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.PropertyStateEvent;

/**
 * The application's main frame.
 */
public class FOOTBALLView extends FrameView {
    Connection conn=null;
    ResultSet rs=null;
    PreparedStatement pst=null;
    public FOOTBALLView(SingleFrameApplication app) {
        super(app);
        initComponents();
        try {
            conn = (Connection)DriverManager.getConnection("jdbc:mysql://localhost:3306/football","root","aurorassss");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
        UpdateBallondor();
        UpdateTopPlayer();


        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
	messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
	messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        }); 
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });

        // tracking table selection
        TopPlayerTable.getSelectionModel().addListSelectionListener(
            new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    firePropertyChange("recordSelected", !isRecordSelected(), isRecordSelected());
                }
            });

        // tracking changes to save
        bindingGroup.addBindingListener(new AbstractBindingListener() {
            @Override
            public void targetChanged(Binding binding, PropertyStateEvent event) {
                // save action observes saveNeeded property
                setSaveNeeded(true);
            }
        });

        // have a transaction started
        entityManager.getTransaction().begin();
    }

    private void UpdateBallondor()
    {
        String sql="Select * from ballondor;";
        try {
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            BallondorTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }

    }
    private void UpdateDual()

    {
         String sql="Select * from duall;";
        try {
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
    }
    private void UpdateTopPlayer()
    {
        String sql="Select * from top_player;";
        try {
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            TopPlayerTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }

    }




    public boolean isSaveNeeded() {
        return saveNeeded;
    }

    private void setSaveNeeded(boolean saveNeeded) {
        if (saveNeeded != this.saveNeeded) {
            this.saveNeeded = saveNeeded;
            firePropertyChange("saveNeeded", !saveNeeded, saveNeeded);
        }
    }

    public boolean isRecordSelected() {
        return TopPlayerTable.getSelectedRow() != -1;
    }
    

    @Action
    public void newRecord() {
        football.TopPlayer t = new football.TopPlayer();
        entityManager.persist(t);
        list.add(t);
        int row = list.size()-1;
        TopPlayerTable.setRowSelectionInterval(row, row);
        TopPlayerTable.scrollRectToVisible(TopPlayerTable.getCellRect(row, 0, true));
        setSaveNeeded(true);
    }

    @Action(enabledProperty = "recordSelected")
    public void deleteRecord() {
        int[] selected = TopPlayerTable.getSelectedRows();
        List<football.TopPlayer> toRemove = new ArrayList<football.TopPlayer>(selected.length);
        for (int idx=0; idx<selected.length; idx++) {
            football.TopPlayer t = list.get(TopPlayerTable.convertRowIndexToModel(selected[idx]));
            toRemove.add(t);
            entityManager.remove(t);
        }
        list.removeAll(toRemove);
        setSaveNeeded(true);
    }
    

    @Action(enabledProperty = "saveNeeded")
    public Task save() {
        return new SaveTask(getApplication());
    }

    private class SaveTask extends Task {
        SaveTask(org.jdesktop.application.Application app) {
            super(app);
        }
        @Override protected Void doInBackground() {
            try {
                entityManager.getTransaction().commit();
                entityManager.getTransaction().begin();
            } catch (RollbackException rex) {
                rex.printStackTrace();
                entityManager.getTransaction().begin();
                List<football.TopPlayer> merged = new ArrayList<football.TopPlayer>(list.size());
                for (football.TopPlayer t : list) {
                    merged.add(entityManager.merge(t));
                }
                list.clear();
                list.addAll(merged);
            }
            return null;
        }
        @Override protected void finished() {
            setSaveNeeded(false);
        }
    }

    /**
     * An example action method showing how to create asynchronous tasks
     * (running on background) and how to show their progress. Note the
     * artificial 'Thread.sleep' calls making the task long enough to see the
     * progress visualization - remove the sleeps for real application.
     */
    @Action
    public Task refresh() {
       return new RefreshTask(getApplication());
    }

    private class RefreshTask extends Task {
        RefreshTask(org.jdesktop.application.Application app) {
            super(app);
        }
        @SuppressWarnings("unchecked")
        @Override protected Void doInBackground() {
            try {
                setProgress(0, 0, 4);
                setMessage("Rolling back the current changes...");
                setProgress(1, 0, 4);
                entityManager.getTransaction().rollback();
                Thread.sleep(1000L); // remove for real app
                setProgress(2, 0, 4);

                setMessage("Starting a new transaction...");
                entityManager.getTransaction().begin();
                Thread.sleep(500L); // remove for real app
                setProgress(3, 0, 4);

                setMessage("Fetching new data...");
                java.util.Collection data = query.getResultList();
                for (Object entity : data) {
                    entityManager.refresh(entity);
                }
                Thread.sleep(1300L); // remove for real app
                setProgress(4, 0, 4);

                Thread.sleep(150L); // remove for real app
                list.clear();
                list.addAll(data);
            } catch(InterruptedException ignore) { }
            return null;
        }
        @Override protected void finished() {
            setMessage("Done.");
            setSaveNeeded(false);
        }
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = FOOTBALLApp.getApplication().getMainFrame();
            aboutBox = new FOOTBALLAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        FOOTBALLApp.getApplication().show(aboutBox);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        mainPanel = new javax.swing.JPanel();
        MainTabbedPanel = new javax.swing.JTabbedPane();
        TopPlayerPanel = new javax.swing.JPanel();
        TopPlayerScrollPanel = new javax.swing.JScrollPane();
        TopPlayerTable = new javax.swing.JTable();
        playerLabelTP = new javax.swing.JLabel();
        goalsLabelTP = new javax.swing.JLabel();
        assistsLabelTP = new javax.swing.JLabel();
        passLabelTP = new javax.swing.JLabel();
        achievementsLabelTP = new javax.swing.JLabel();
        newTP = new javax.swing.JButton();
        deleteTP = new javax.swing.JButton();
        searchTP = new javax.swing.JButton();
        refreshTP = new javax.swing.JButton();
        alteraddTP = new javax.swing.JButton();
        newcolLabelTP = new javax.swing.JLabel();
        newcolFieldTP = new javax.swing.JTextField();
        alterdelTP = new javax.swing.JButton();
        savepointFieldTP = new javax.swing.JTextField();
        rollbackFieldTP = new javax.swing.JTextField();
        savepointLabelTP = new javax.swing.JLabel();
        rollbackLabelTP = new javax.swing.JLabel();
        savepointTP = new javax.swing.JButton();
        rollbackTP = new javax.swing.JButton();
        playerFieldTP = new javax.swing.JTextField();
        goalsFieldTP = new javax.swing.JTextField();
        passFieldTP = new javax.swing.JTextField();
        assistsFieldTP = new javax.swing.JTextField();
        achievementsFieldTP = new javax.swing.JTextField();
        updateTP = new javax.swing.JButton();
        BallondorPanel = new javax.swing.JPanel();
        BallondorScrollPanel = new javax.swing.JScrollPane();
        BallondorTable = new javax.swing.JTable();
        playerLabelBD = new javax.swing.JLabel();
        goalsLabelBD = new javax.swing.JLabel();
        assistsLabelBD = new javax.swing.JLabel();
        trophiesLabelBD = new javax.swing.JLabel();
        votesLabelBD = new javax.swing.JLabel();
        playerFieldBD = new javax.swing.JTextField();
        goalsFieldBD = new javax.swing.JTextField();
        assistsFieldBD = new javax.swing.JTextField();
        trophiesFieldBD = new javax.swing.JTextField();
        votesFieldBD = new javax.swing.JTextField();
        newBD = new javax.swing.JButton();
        deleteBD = new javax.swing.JButton();
        updateBD = new javax.swing.JButton();
        searchBD = new javax.swing.JButton();
        refreshBD = new javax.swing.JButton();
        alteraddBD = new javax.swing.JButton();
        alterdelBD = new javax.swing.JButton();
        newcolFieldBD = new javax.swing.JTextField();
        newcolLabelBD = new javax.swing.JLabel();
        savepointFieldBD = new javax.swing.JTextField();
        rollbackFieldBD = new javax.swing.JTextField();
        savepointLabelBD = new javax.swing.JLabel();
        rollbackLabelBD = new javax.swing.JLabel();
        savepointBD = new javax.swing.JButton();
        rollbackBD = new javax.swing.JButton();
        DualPanel = new javax.swing.JPanel();
        DualScrollPane = new javax.swing.JScrollPane();
        DualTable = new javax.swing.JTable();
        v1Field = new javax.swing.JTextField();
        v2Field = new javax.swing.JTextField();
        v1Label = new javax.swing.JLabel();
        v2Label = new javax.swing.JLabel();
        ClearDual = new javax.swing.JButton();
        greatestDual = new javax.swing.JButton();
        leastDual = new javax.swing.JButton();
        avgDual = new javax.swing.JButton();
        adddateDual = new javax.swing.JButton();
        lengthDual = new javax.swing.JButton();
        concatDual = new javax.swing.JButton();
        JoinPanel = new javax.swing.JPanel();
        JoinScrollPane = new javax.swing.JScrollPane();
        JoinTable = new javax.swing.JTable();
        nJoin = new javax.swing.JButton();
        cJoin = new javax.swing.JButton();
        loJoin = new javax.swing.JButton();
        roJoin = new javax.swing.JButton();
        clearJoin = new javax.swing.JButton();
        nested = new javax.swing.JButton();
        commit = new javax.swing.JButton();
        starttransaction = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem newRecordMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem deleteRecordMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        javax.swing.JMenuItem saveMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem refreshMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(football.FOOTBALLApp.class).getContext().getResourceMap(FOOTBALLView.class);
        entityManager = java.beans.Beans.isDesignTime() ? null : javax.persistence.Persistence.createEntityManagerFactory(resourceMap.getString("entityManager.persistenceUnit")).createEntityManager(); // NOI18N
        query = java.beans.Beans.isDesignTime() ? null : entityManager.createQuery(resourceMap.getString("query.query")); // NOI18N
        list = java.beans.Beans.isDesignTime() ? java.util.Collections.emptyList() : org.jdesktop.observablecollections.ObservableCollections.observableList(query.getResultList());

        mainPanel.setName("mainPanel"); // NOI18N

        MainTabbedPanel.setBackground(resourceMap.getColor("MainTabbedPanel.background")); // NOI18N
        MainTabbedPanel.setName("MainTabbedPanel"); // NOI18N

        TopPlayerPanel.setBackground(resourceMap.getColor("TopPlayerPanel.background")); // NOI18N
        TopPlayerPanel.setName("TopPlayerPanel"); // NOI18N
        TopPlayerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                TopPlayerPanelMouseClicked(evt);
            }
        });

        TopPlayerScrollPanel.setName("TopPlayerScrollPanel"); // NOI18N

        TopPlayerTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Player", "Goals", "Assists", "Pass", "Achievements"
            }
        ));
        TopPlayerTable.setName("TopPlayerTable"); // NOI18N
        TopPlayerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                TopPlayerTableMouseClicked(evt);
            }
        });
        TopPlayerScrollPanel.setViewportView(TopPlayerTable);
        TopPlayerTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("TopPlayerTable.columnModel.title0")); // NOI18N
        TopPlayerTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("TopPlayerTable.columnModel.title1")); // NOI18N
        TopPlayerTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("TopPlayerTable.columnModel.title2")); // NOI18N
        TopPlayerTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("TopPlayerTable.columnModel.title3")); // NOI18N
        TopPlayerTable.getColumnModel().getColumn(4).setHeaderValue(resourceMap.getString("TopPlayerTable.columnModel.title4")); // NOI18N

        playerLabelTP.setText(resourceMap.getString("playerLabelTP.text")); // NOI18N
        playerLabelTP.setName("playerLabelTP"); // NOI18N

        goalsLabelTP.setText(resourceMap.getString("goalsLabelTP.text")); // NOI18N
        goalsLabelTP.setName("goalsLabelTP"); // NOI18N

        assistsLabelTP.setText(resourceMap.getString("assistsLabelTP.text")); // NOI18N
        assistsLabelTP.setName("assistsLabelTP"); // NOI18N

        passLabelTP.setText(resourceMap.getString("passLabelTP.text")); // NOI18N
        passLabelTP.setName("passLabelTP"); // NOI18N

        achievementsLabelTP.setText(resourceMap.getString("achievementsLabelTP.text")); // NOI18N
        achievementsLabelTP.setName("achievementsLabelTP"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(football.FOOTBALLApp.class).getContext().getActionMap(FOOTBALLView.class, this);
        newTP.setAction(actionMap.get("newRecord")); // NOI18N
        newTP.setName("newTP");
        newTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTPActionPerformed(evt);
            }
        });

        deleteTP.setAction(actionMap.get("deleteRecord")); // NOI18N
        deleteTP.setName("deleteTP"); // NOI18N
        deleteTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTPActionPerformed(evt);
            }
        });

        searchTP.setText(resourceMap.getString("searchTP.text")); // NOI18N
        searchTP.setName("searchTP"); // NOI18N
        searchTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchTPActionPerformed(evt);
            }
        });

        refreshTP.setText(resourceMap.getString("refreshTP.text")); // NOI18N
        refreshTP.setName("refreshTP"); // NOI18N
        refreshTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshTPActionPerformed(evt);
            }
        });

        alteraddTP.setText(resourceMap.getString("alteraddTP.text")); // NOI18N
        alteraddTP.setName("alteraddTP"); // NOI18N
        alteraddTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alteraddTPActionPerformed(evt);
            }
        });

        newcolLabelTP.setText(resourceMap.getString("newcolLabelTP.text")); // NOI18N
        newcolLabelTP.setName("newcolLabelTP"); // NOI18N

        newcolFieldTP.setText(resourceMap.getString("newcolFieldTP.text")); // NOI18N
        newcolFieldTP.setName("newcolFieldTP"); // NOI18N

        alterdelTP.setText(resourceMap.getString("alterdelTP.text")); // NOI18N
        alterdelTP.setName("alterdelTP"); // NOI18N
        alterdelTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alterdelTPActionPerformed(evt);
            }
        });

        savepointFieldTP.setText(resourceMap.getString("savepointFieldTP.text")); // NOI18N
        savepointFieldTP.setName("savepointFieldTP"); // NOI18N

        rollbackFieldTP.setText(resourceMap.getString("rollbackFieldTP.text")); // NOI18N
        rollbackFieldTP.setName("rollbackFieldTP"); // NOI18N

        savepointLabelTP.setText(resourceMap.getString("savepointLabelTP.text")); // NOI18N
        savepointLabelTP.setName("savepointLabelTP"); // NOI18N

        rollbackLabelTP.setText(resourceMap.getString("rollbackLabelTP.text")); // NOI18N
        rollbackLabelTP.setName("rollbackLabelTP"); // NOI18N

        savepointTP.setText(resourceMap.getString("savepointTP.text")); // NOI18N
        savepointTP.setName("savepointTP"); // NOI18N
        savepointTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savepointTPActionPerformed(evt);
            }
        });

        rollbackTP.setText(resourceMap.getString("rollbackTP.text")); // NOI18N
        rollbackTP.setName("rollbackTP"); // NOI18N
        rollbackTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rollbackTPActionPerformed(evt);
            }
        });

        playerFieldTP.setText(resourceMap.getString("playerFieldTP.text")); // NOI18N
        playerFieldTP.setName("playerFieldTP"); // NOI18N

        goalsFieldTP.setText(resourceMap.getString("goalsFieldTP.text")); // NOI18N
        goalsFieldTP.setName("goalsFieldTP"); // NOI18N

        passFieldTP.setName("passFieldTP"); // NOI18N

        assistsFieldTP.setName("assistsFieldTP"); // NOI18N

        achievementsFieldTP.setName("achievementsFieldTP"); // NOI18N

        updateTP.setText(resourceMap.getString("updateTP.text")); // NOI18N
        updateTP.setName("updateTP"); // NOI18N
        updateTP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateTPActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout TopPlayerPanelLayout = new javax.swing.GroupLayout(TopPlayerPanel);
        TopPlayerPanel.setLayout(TopPlayerPanelLayout);
        TopPlayerPanelLayout.setHorizontalGroup(
            TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(TopPlayerScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 522, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addComponent(newTP)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(updateTP, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteTP)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchTP)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(refreshTP))
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addGap(37, 37, 37)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(playerLabelTP, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(goalsLabelTP)
                                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addComponent(assistsLabelTP)
                                            .addComponent(passLabelTP))))
                                .addGap(18, 18, 18)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(passFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(playerFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(goalsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(assistsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addComponent(achievementsLabelTP)
                                .addGap(18, 18, 18)
                                .addComponent(achievementsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 269, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 99, Short.MAX_VALUE)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(newcolLabelTP)
                                    .addComponent(alteraddTP))
                                .addGap(18, 18, 18)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(alterdelTP)
                                    .addComponent(newcolFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(savepointLabelTP)
                                    .addComponent(rollbackLabelTP, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(savepointFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(rollbackFieldTP)
                                        .addComponent(savepointTP)
                                        .addComponent(rollbackTP)))))))
                .addContainerGap(358, Short.MAX_VALUE))
        );

        TopPlayerPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deleteTP, newTP});

        TopPlayerPanelLayout.setVerticalGroup(
            TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                .addContainerGap(69, Short.MAX_VALUE)
                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, TopPlayerPanelLayout.createSequentialGroup()
                        .addComponent(TopPlayerScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(42, 42, 42)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(playerLabelTP)
                            .addComponent(playerFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(goalsLabelTP)
                            .addComponent(goalsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(savepointFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(savepointLabelTP))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(savepointTP)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(rollbackFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(rollbackLabelTP))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rollbackTP))
                            .addGroup(TopPlayerPanelLayout.createSequentialGroup()
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(assistsLabelTP)
                                    .addComponent(assistsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(passLabelTP)
                                    .addComponent(passFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(achievementsLabelTP)
                                    .addComponent(achievementsFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newTP)
                            .addComponent(deleteTP)
                            .addComponent(searchTP)
                            .addComponent(refreshTP)
                            .addComponent(updateTP))
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, TopPlayerPanelLayout.createSequentialGroup()
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newcolLabelTP)
                            .addComponent(newcolFieldTP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(TopPlayerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(alteraddTP)
                            .addComponent(alterdelTP))
                        .addGap(174, 174, 174))))
        );

        MainTabbedPanel.addTab(resourceMap.getString("TopPlayerPanel.TabConstraints.tabTitle"), TopPlayerPanel); // NOI18N

        BallondorPanel.setBackground(resourceMap.getColor("BallondorPanel.background")); // NOI18N
        BallondorPanel.setName("BallondorPanel"); // NOI18N
        BallondorPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                BallondorPanelMouseClicked(evt);
            }
        });

        BallondorScrollPanel.setAutoscrolls(true);
        BallondorScrollPanel.setName("BallondorScrollPanel"); // NOI18N
        BallondorScrollPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                BallondorScrollPanelMouseClicked(evt);
            }
        });

        BallondorTable.setName("BallondorTable"); // NOI18N

        org.jdesktop.swingbinding.JTableBinding jTableBinding = org.jdesktop.swingbinding.SwingBindings.createJTableBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, list, BallondorTable);
        org.jdesktop.swingbinding.JTableBinding.ColumnBinding columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${Player}"));
        columnBinding.setColumnName("Player");
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${Goals}"));
        columnBinding.setColumnName("Goals");
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${Assists}"));
        columnBinding.setColumnName("Assists");
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${Trophies}"));
        columnBinding.setColumnName("Trophies");
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${Votes}"));
        columnBinding.setColumnName("Votes");
        bindingGroup.addBinding(jTableBinding);
        jTableBinding.bind();
        BallondorTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                BallondorTableMouseClicked(evt);
            }
        });
        BallondorTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                BallondorTableKeyPressed(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                BallondorTableKeyTyped(evt);
            }
        });
        BallondorScrollPanel.setViewportView(BallondorTable);
        BallondorTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("BallondorTable.columnModel.title0")); // NOI18N
        BallondorTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("BallondorTable.columnModel.title1")); // NOI18N
        BallondorTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("BallondorTable.columnModel.title2")); // NOI18N
        BallondorTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("BallondorTable.columnModel.title3")); // NOI18N
        BallondorTable.getColumnModel().getColumn(4).setHeaderValue(resourceMap.getString("BallondorTable.columnModel.title4")); // NOI18N

        playerLabelBD.setText(resourceMap.getString("playerLabelBD.text")); // NOI18N
        playerLabelBD.setName("playerLabelBD"); // NOI18N

        goalsLabelBD.setText(resourceMap.getString("goalsLabelBD.text")); // NOI18N
        goalsLabelBD.setName("goalsLabelBD"); // NOI18N

        assistsLabelBD.setText(resourceMap.getString("assistsLabelBD.text")); // NOI18N
        assistsLabelBD.setName("assistsLabelBD"); // NOI18N

        trophiesLabelBD.setText(resourceMap.getString("trophiesLabelBD.text")); // NOI18N
        trophiesLabelBD.setName("trophiesLabelBD"); // NOI18N

        votesLabelBD.setText(resourceMap.getString("votesLabelBD.text")); // NOI18N
        votesLabelBD.setName("votesLabelBD"); // NOI18N

        playerFieldBD.setText(resourceMap.getString("playerFieldBD.text")); // NOI18N
        playerFieldBD.setName("playerFieldBD"); // NOI18N

        goalsFieldBD.setText(resourceMap.getString("goalsFieldBD.text")); // NOI18N
        goalsFieldBD.setName("goalsFieldBD"); // NOI18N

        assistsFieldBD.setText(resourceMap.getString("assistsFieldBD.text")); // NOI18N
        assistsFieldBD.setName("assistsFieldBD"); // NOI18N

        trophiesFieldBD.setText(resourceMap.getString("trophiesFieldBD.text")); // NOI18N
        trophiesFieldBD.setName("trophiesFieldBD"); // NOI18N

        votesFieldBD.setText(resourceMap.getString("votesFieldBD.text")); // NOI18N
        votesFieldBD.setName("votesFieldBD"); // NOI18N

        newBD.setText(resourceMap.getString("newBD.text")); // NOI18N
        newBD.setName("newBD"); // NOI18N
        newBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newBDActionPerformed(evt);
            }
        });

        deleteBD.setText(resourceMap.getString("deleteBD.text")); // NOI18N
        deleteBD.setName("deleteBD"); // NOI18N
        deleteBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteBDActionPerformed(evt);
            }
        });

        updateBD.setText(resourceMap.getString("updateBD.text")); // NOI18N
        updateBD.setName("updateBD"); // NOI18N
        updateBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateBDActionPerformed(evt);
            }
        });

        searchBD.setText(resourceMap.getString("searchBD.text")); // NOI18N
        searchBD.setName("searchBD"); // NOI18N
        searchBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchBDActionPerformed(evt);
            }
        });

        refreshBD.setText(resourceMap.getString("refreshBD.text")); // NOI18N
        refreshBD.setName("refreshBD"); // NOI18N
        refreshBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshBDActionPerformed(evt);
            }
        });

        alteraddBD.setText(resourceMap.getString("alteraddBD.text")); // NOI18N
        alteraddBD.setName("alteraddBD"); // NOI18N
        alteraddBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alteraddBDActionPerformed(evt);
            }
        });

        alterdelBD.setText(resourceMap.getString("alterdelBD.text")); // NOI18N
        alterdelBD.setName("alterdelBD"); // NOI18N
        alterdelBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alterdelBDActionPerformed(evt);
            }
        });

        newcolFieldBD.setName("newcolFieldBD"); // NOI18N

        newcolLabelBD.setText(resourceMap.getString("newcolLabelBD.text")); // NOI18N
        newcolLabelBD.setName("newcolLabelBD"); // NOI18N

        savepointFieldBD.setName("savepointFieldBD"); // NOI18N

        rollbackFieldBD.setName("rollbackFieldBD"); // NOI18N

        savepointLabelBD.setText(resourceMap.getString("savepointLabelBD.text")); // NOI18N
        savepointLabelBD.setName("savepointLabelBD"); // NOI18N

        rollbackLabelBD.setText(resourceMap.getString("rollbackLabelBD.text")); // NOI18N
        rollbackLabelBD.setName("rollbackLabelBD"); // NOI18N

        savepointBD.setText(resourceMap.getString("savepointBD.text")); // NOI18N
        savepointBD.setName("savepointBD"); // NOI18N
        savepointBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savepointBDActionPerformed(evt);
            }
        });

        rollbackBD.setText(resourceMap.getString("rollbackBD.text")); // NOI18N
        rollbackBD.setName("rollbackBD"); // NOI18N
        rollbackBD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rollbackBDActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout BallondorPanelLayout = new javax.swing.GroupLayout(BallondorPanel);
        BallondorPanel.setLayout(BallondorPanelLayout);
        BallondorPanelLayout.setHorizontalGroup(
            BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BallondorPanelLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(BallondorScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 577, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(playerLabelBD)
                            .addComponent(assistsLabelBD))
                        .addGap(25, 25, 25)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(playerFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(goalsFieldBD, javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(assistsFieldBD, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 34, Short.MAX_VALUE)))
                        .addGap(72, 72, 72)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(newcolLabelBD)
                            .addComponent(alteraddBD))
                        .addGap(18, 18, 18)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(alterdelBD)
                            .addComponent(newcolFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(goalsLabelBD)
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(BallondorPanelLayout.createSequentialGroup()
                                .addComponent(votesLabelBD)
                                .addGap(32, 32, 32)
                                .addComponent(votesFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(BallondorPanelLayout.createSequentialGroup()
                                .addComponent(newBD)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(updateBD)
                                .addGap(10, 10, 10)
                                .addComponent(deleteBD)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchBD)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(refreshBD))
                            .addGroup(BallondorPanelLayout.createSequentialGroup()
                                .addComponent(trophiesLabelBD)
                                .addGap(18, 18, 18)
                                .addComponent(trophiesFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 283, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(BallondorPanelLayout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addComponent(rollbackLabelBD))
                            .addGroup(BallondorPanelLayout.createSequentialGroup()
                                .addComponent(savepointLabelBD)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(savepointFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(rollbackFieldBD)
                                        .addComponent(savepointBD)
                                        .addComponent(rollbackBD)))))))
                .addContainerGap(439, Short.MAX_VALUE))
        );
        BallondorPanelLayout.setVerticalGroup(
            BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(BallondorPanelLayout.createSequentialGroup()
                .addGap(38, 38, 38)
                .addComponent(BallondorScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(playerLabelBD)
                            .addComponent(playerFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(8, 8, 8)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(goalsLabelBD)
                            .addComponent(goalsFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(8, 8, 8)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(assistsLabelBD)
                            .addComponent(assistsFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newcolLabelBD)
                            .addComponent(newcolFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(alteraddBD)
                            .addComponent(alterdelBD))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(trophiesLabelBD)
                            .addComponent(trophiesFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 25, Short.MAX_VALUE)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(votesLabelBD)
                            .addComponent(votesFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newBD)
                            .addComponent(searchBD)
                            .addComponent(refreshBD)
                            .addComponent(updateBD)
                            .addComponent(deleteBD))
                        .addGap(12, 12, 12))
                    .addGroup(BallondorPanelLayout.createSequentialGroup()
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(savepointFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(savepointLabelBD))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(savepointBD)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(BallondorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(rollbackFieldBD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(rollbackLabelBD))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rollbackBD)
                        .addContainerGap())))
        );

        MainTabbedPanel.addTab(resourceMap.getString("BallondorPanel.TabConstraints.tabTitle"), BallondorPanel); // NOI18N

        DualPanel.setBackground(resourceMap.getColor("DualPanel.background")); // NOI18N
        DualPanel.setName("DualPanel"); // NOI18N
        DualPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                DualPanelMouseClicked(evt);
            }
        });

        DualScrollPane.setName("DualScrollPane"); // NOI18N
        DualScrollPane.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                DualScrollPaneMouseClicked(evt);
            }
        });

        DualTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null}
            },
            new String [] {
                "Result"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        DualTable.setName("DualTable"); // NOI18N
        DualTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                DualTableMouseClicked(evt);
            }
        });
        DualScrollPane.setViewportView(DualTable);
        DualTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("DualTable.columnModel.title0")); // NOI18N

        v1Field.setText(resourceMap.getString("v1Field.text")); // NOI18N
        v1Field.setName("v1Field"); // NOI18N

        v2Field.setText(resourceMap.getString("v2Field.text")); // NOI18N
        v2Field.setName("v2Field"); // NOI18N

        v1Label.setText(resourceMap.getString("v1Label.text")); // NOI18N
        v1Label.setName("v1Label"); // NOI18N

        v2Label.setText(resourceMap.getString("v2Label.text")); // NOI18N
        v2Label.setName("v2Label"); // NOI18N

        ClearDual.setText(resourceMap.getString("ClearDual.text")); // NOI18N
        ClearDual.setName("ClearDual"); // NOI18N
        ClearDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ClearDualActionPerformed(evt);
            }
        });

        greatestDual.setText(resourceMap.getString("greatestDual.text")); // NOI18N
        greatestDual.setName("greatestDual"); // NOI18N
        greatestDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                greatestDualActionPerformed(evt);
            }
        });

        leastDual.setText(resourceMap.getString("leastDual.text")); // NOI18N
        leastDual.setName("leastDual"); // NOI18N
        leastDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                leastDualActionPerformed(evt);
            }
        });

        avgDual.setText(resourceMap.getString("avgDual.text")); // NOI18N
        avgDual.setName("avgDual"); // NOI18N
        avgDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                avgDualActionPerformed(evt);
            }
        });

        adddateDual.setText(resourceMap.getString("adddateDual.text")); // NOI18N
        adddateDual.setName("adddateDual"); // NOI18N
        adddateDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                adddateDualActionPerformed(evt);
            }
        });

        lengthDual.setText(resourceMap.getString("lengthDual.text")); // NOI18N
        lengthDual.setName("lengthDual"); // NOI18N
        lengthDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lengthDualActionPerformed(evt);
            }
        });

        concatDual.setText(resourceMap.getString("concatDual.text")); // NOI18N
        concatDual.setName("concatDual"); // NOI18N
        concatDual.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                concatDualActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout DualPanelLayout = new javax.swing.GroupLayout(DualPanel);
        DualPanel.setLayout(DualPanelLayout);
        DualPanelLayout.setHorizontalGroup(
            DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DualPanelLayout.createSequentialGroup()
                .addGap(37, 37, 37)
                .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(DualPanelLayout.createSequentialGroup()
                        .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(v1Label)
                            .addComponent(v2Label))
                        .addGap(28, 28, 28)
                        .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(v2Field, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(v1Field, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 106, Short.MAX_VALUE)))
                    .addGroup(DualPanelLayout.createSequentialGroup()
                        .addComponent(ClearDual)
                        .addGap(18, 18, 18)
                        .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(adddateDual, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(greatestDual, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(leastDual, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lengthDual, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(avgDual)
                            .addComponent(concatDual)))
                    .addComponent(DualScrollPane, 0, 0, Short.MAX_VALUE))
                .addContainerGap(691, Short.MAX_VALUE))
        );
        DualPanelLayout.setVerticalGroup(
            DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DualPanelLayout.createSequentialGroup()
                .addGap(36, 36, 36)
                .addComponent(DualScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(40, 40, 40)
                .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(v1Field, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(v1Label))
                .addGap(18, 18, 18)
                .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(v2Field, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(v2Label))
                .addGap(80, 80, 80)
                .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ClearDual)
                    .addComponent(greatestDual)
                    .addComponent(leastDual)
                    .addComponent(avgDual))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(DualPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(adddateDual)
                    .addComponent(lengthDual)
                    .addComponent(concatDual))
                .addContainerGap(117, Short.MAX_VALUE))
        );

        MainTabbedPanel.addTab(resourceMap.getString("DualPanel.TabConstraints.tabTitle"), DualPanel); // NOI18N

        JoinPanel.setBackground(resourceMap.getColor("JoinPanel.background")); // NOI18N
        JoinPanel.setName("JoinPanel"); // NOI18N

        JoinScrollPane.setName("JoinScrollPane"); // NOI18N

        JoinTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4", "Title 5", "Title 6", "Title 7", "Title 8", "Title 9", "Title 10"
            }
        ));
        JoinTable.setName("JoinTable"); // NOI18N
        JoinScrollPane.setViewportView(JoinTable);
        JoinTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title0")); // NOI18N
        JoinTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title1")); // NOI18N
        JoinTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title2")); // NOI18N
        JoinTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title3")); // NOI18N
        JoinTable.getColumnModel().getColumn(4).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title4")); // NOI18N
        JoinTable.getColumnModel().getColumn(5).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title5")); // NOI18N
        JoinTable.getColumnModel().getColumn(6).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title6")); // NOI18N
        JoinTable.getColumnModel().getColumn(7).setHeaderValue(resourceMap.getString("JoinTable.columnModel.title7")); // NOI18N

        nJoin.setText(resourceMap.getString("nJoin.text")); // NOI18N
        nJoin.setName("nJoin"); // NOI18N
        nJoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nJoinActionPerformed(evt);
            }
        });

        cJoin.setText(resourceMap.getString("cJoin.text")); // NOI18N
        cJoin.setName("cJoin"); // NOI18N
        cJoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cJoinActionPerformed(evt);
            }
        });

        loJoin.setText(resourceMap.getString("loJoin.text")); // NOI18N
        loJoin.setName("loJoin"); // NOI18N
        loJoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loJoinActionPerformed(evt);
            }
        });

        roJoin.setText(resourceMap.getString("roJoin.text")); // NOI18N
        roJoin.setName("roJoin"); // NOI18N
        roJoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                roJoinActionPerformed(evt);
            }
        });

        clearJoin.setText(resourceMap.getString("clearJoin.text")); // NOI18N
        clearJoin.setName("clearJoin"); // NOI18N
        clearJoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearJoinActionPerformed(evt);
            }
        });

        nested.setText(resourceMap.getString("nested.text")); // NOI18N
        nested.setName("nested"); // NOI18N
        nested.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nestedActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout JoinPanelLayout = new javax.swing.GroupLayout(JoinPanel);
        JoinPanel.setLayout(JoinPanelLayout);
        JoinPanelLayout.setHorizontalGroup(
            JoinPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(JoinPanelLayout.createSequentialGroup()
                .addGroup(JoinPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(JoinPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(JoinScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 607, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(JoinPanelLayout.createSequentialGroup()
                        .addGap(36, 36, 36)
                        .addComponent(nJoin)
                        .addGap(27, 27, 27)
                        .addComponent(cJoin)
                        .addGap(68, 68, 68)
                        .addComponent(loJoin)
                        .addGap(28, 28, 28)
                        .addComponent(roJoin)
                        .addGap(18, 18, 18)
                        .addComponent(nested))
                    .addGroup(JoinPanelLayout.createSequentialGroup()
                        .addGap(213, 213, 213)
                        .addComponent(clearJoin, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(416, Short.MAX_VALUE))
        );
        JoinPanelLayout.setVerticalGroup(
            JoinPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(JoinPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(JoinScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 281, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(25, 25, 25)
                .addGroup(JoinPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nJoin)
                    .addComponent(cJoin)
                    .addComponent(loJoin)
                    .addComponent(roJoin)
                    .addComponent(nested))
                .addGap(28, 28, 28)
                .addComponent(clearJoin, javax.swing.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
                .addContainerGap())
        );

        MainTabbedPanel.addTab(resourceMap.getString("JoinPanel.TabConstraints.tabTitle"), JoinPanel); // NOI18N

        commit.setText(resourceMap.getString("commit.text")); // NOI18N
        commit.setName("commit"); // NOI18N
        commit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                commitActionPerformed(evt);
            }
        });

        starttransaction.setText(resourceMap.getString("starttransaction.text")); // NOI18N
        starttransaction.setName("starttransaction"); // NOI18N
        starttransaction.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                starttransactionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(MainTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 841, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(commit)
                        .addGap(18, 18, 18)
                        .addComponent(starttransaction)
                        .addGap(616, 616, 616))))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(MainTabbedPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 459, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(commit)
                    .addComponent(starttransaction))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        newRecordMenuItem.setAction(actionMap.get("newRecord")); // NOI18N
        newRecordMenuItem.setName("newRecordMenuItem"); // NOI18N
        fileMenu.add(newRecordMenuItem);

        deleteRecordMenuItem.setAction(actionMap.get("deleteRecord")); // NOI18N
        deleteRecordMenuItem.setName("deleteRecordMenuItem"); // NOI18N
        fileMenu.add(deleteRecordMenuItem);

        jSeparator1.setName("jSeparator1"); // NOI18N
        fileMenu.add(jSeparator1);

        saveMenuItem.setAction(actionMap.get("save")); // NOI18N
        saveMenuItem.setName("saveMenuItem"); // NOI18N
        fileMenu.add(saveMenuItem);

        refreshMenuItem.setAction(actionMap.get("refresh")); // NOI18N
        refreshMenuItem.setName("refreshMenuItem"); // NOI18N
        fileMenu.add(refreshMenuItem);

        jSeparator2.setName("jSeparator2"); // NOI18N
        fileMenu.add(jSeparator2);

        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 691, Short.MAX_VALUE)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 861, Short.MAX_VALUE)
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(statusMessageLabel)
                    .addComponent(statusAnimationLabel)
                    .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void newTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTPActionPerformed
         try{

           String sql=("insert into top_player(player, goals, assists, pass, achievements) values ('"+playerFieldTP.getText()+"',"+goalsFieldTP.getText()+","+assistsFieldTP.getText()+","+passFieldTP.getText()+",'"+achievementsFieldTP.getText()+"')");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();

        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }// TODO add your handling code h
    }//GEN-LAST:event_newTPActionPerformed

    private void BallondorScrollPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_BallondorScrollPanelMouseClicked
        // TODO add your handling code here:
    }//GEN-LAST:event_BallondorScrollPanelMouseClicked

    private void BallondorTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_BallondorTableMouseClicked
            // get the model from the jtable
       DefaultTableModel model = (DefaultTableModel)BallondorTable.getModel();

        // get the selected row index
       int selectedRowIndex = BallondorTable.getSelectedRow();

        // set the selected row data into jtextfields
       playerFieldBD.setText(model.getValueAt(selectedRowIndex, 0).toString());
       goalsFieldBD.setText(model.getValueAt(selectedRowIndex, 1).toString());
       assistsFieldBD.setText(model.getValueAt(selectedRowIndex, 2).toString());
       trophiesFieldBD.setText(model.getValueAt(selectedRowIndex, 3).toString());
       votesFieldBD.setText(model.getValueAt(selectedRowIndex, 4).toString()); // TODO add your handling code here:
    }//GEN-LAST:event_BallondorTableMouseClicked

    private void BallondorPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_BallondorPanelMouseClicked
        // TODO add your handling code here:
        DefaultTableModel model = (DefaultTableModel)BallondorTable.getModel();
        playerFieldBD.setText("");
       goalsFieldBD.setText("");
       assistsFieldBD.setText("");
       trophiesFieldBD.setText("");
       votesFieldBD.setText("");

    }//GEN-LAST:event_BallondorPanelMouseClicked

    private void TopPlayerPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_TopPlayerPanelMouseClicked
     
          DefaultTableModel model = (DefaultTableModel)TopPlayerTable.getModel();
        playerFieldTP.setText("");
       goalsFieldTP.setText("");
       assistsFieldTP.setText("");
       passFieldTP.setText("");
       achievementsFieldTP.setText("");// TODO add your handling code here:
    }//GEN-LAST:event_TopPlayerPanelMouseClicked

    private void BallondorTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_BallondorTableKeyPressed
        // TODO add your handling code here:
    }//GEN-LAST:event_BallondorTableKeyPressed

    private void BallondorTableKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_BallondorTableKeyTyped
        // TODO add your handling code here:
    }//GEN-LAST:event_BallondorTableKeyTyped

    private void newBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newBDActionPerformed

        try{

           String sql=("insert into ballondor(player, goals, assists, trophies, votes) values ('"+playerFieldBD.getText()+"','"+goalsFieldBD.getText()+"','"+assistsFieldBD.getText()+"','"+trophiesFieldBD.getText()+"',"+votesFieldBD.getText()+")");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();
            
        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }// TODO add your handling code here:
    }//GEN-LAST:event_newBDActionPerformed

    private void deleteBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBDActionPerformed
        try{

           String sql=("delete from ballondor where player = '"+playerFieldBD.getText()+"'");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();

        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }// TODO add your handling code here:
    }//GEN-LAST:event_deleteBDActionPerformed

    private void updateBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateBDActionPerformed
        try{

           String sql=("update ballondor set player = '"+playerFieldBD.getText()+"',goals="+goalsFieldBD.getText()+",assists="+assistsFieldBD.getText()+",trophies='"+trophiesFieldBD.getText()+"',votes="+votesFieldBD.getText()+" where player='"+playerFieldBD.getText()+"'");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();

        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }// TODO add your handling code here:
    }//GEN-LAST:event_updateBDActionPerformed

    private void searchBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchBDActionPerformed
      try {
          String sql="Select * from ballondor where player ='"+playerFieldBD.getText()+"'";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            BallondorTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
    }//GEN-LAST:event_searchBDActionPerformed

    private void searchTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchTPActionPerformed
    try {
          String sql="Select * from top_player where player ='"+playerFieldTP.getText()+"'";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            TopPlayerTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }        // TODO add your handling code here:
    }//GEN-LAST:event_searchTPActionPerformed

    private void refreshBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshBDActionPerformed
       UpdateBallondor(); // TODO add your handling code here:
    }//GEN-LAST:event_refreshBDActionPerformed

    private void refreshTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshTPActionPerformed
       UpdateTopPlayer();        // TODO add your handling code here:
    }//GEN-LAST:event_refreshTPActionPerformed

    private void DualScrollPaneMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_DualScrollPaneMouseClicked
        // TODO add your handling code here:
    }//GEN-LAST:event_DualScrollPaneMouseClicked

    private void DualTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_DualTableMouseClicked
       
    }//GEN-LAST:event_DualTableMouseClicked

    private void DualPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_DualPanelMouseClicked

        DefaultTableModel model = (DefaultTableModel)DualTable.getModel();
        v1Field.setText("");
        v2Field.setText("");
        
    }//GEN-LAST:event_DualPanelMouseClicked

    private void ClearDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ClearDualActionPerformed

        UpdateDual();        // TODO add your handling code here:
    }//GEN-LAST:event_ClearDualActionPerformed

    private void greatestDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_greatestDualActionPerformed

         try {
          String sql="Select greatest("+v1Field.getText()+","+v2Field.getText()+")";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
    }//GEN-LAST:event_greatestDualActionPerformed

    private void leastDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_leastDualActionPerformed
        try {
          String sql="Select least("+v1Field.getText()+","+v2Field.getText()+")";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
        // TODO add your handling code here:
    }//GEN-LAST:event_leastDualActionPerformed

    private void avgDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_avgDualActionPerformed
        try {
          String sql="Select avg("+v1Field.getText()+") from "+v2Field.getText();
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        }
       // TODO add your handling code here:
    }//GEN-LAST:event_avgDualActionPerformed

    private void adddateDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_adddateDualActionPerformed
       try {
          String sql="Select ADDDATE('"+v1Field.getText()+"', INTERVAL " +v2Field.getText()+ " day);";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);
        } // TODO add your handling code here:
    }//GEN-LAST:event_adddateDualActionPerformed

    private void lengthDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lengthDualActionPerformed
       try {
          String sql="Select CHAR_LENGTH('"+v1Field.getText()+"')";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);} // TODO add your handling code here:
    }//GEN-LAST:event_lengthDualActionPerformed

    private void concatDualActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_concatDualActionPerformed
       try {
          String sql="Select CONCAT('"+v1Field.getText()+"','"+v2Field.getText()+"')";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            DualTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);} // TODO add your handling code here:
    }//GEN-LAST:event_concatDualActionPerformed

    private void nJoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nJoinActionPerformed
 try {
          String sql="Select * from top_player natural join ballondor";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}    }//GEN-LAST:event_nJoinActionPerformed

    private void cJoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cJoinActionPerformed
        try {
          String sql="Select * from top_player cross join ballondor";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_cJoinActionPerformed

    private void loJoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loJoinActionPerformed
       try {
          String sql=" Select * from top_player left join ballondor on top_player.player=ballondor.player";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_loJoinActionPerformed

    private void roJoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roJoinActionPerformed
       try {
          String sql=" Select * from top_player right join ballondor on top_player.player=ballondor.player";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_roJoinActionPerformed

    private void clearJoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearJoinActionPerformed
        try {
          String sql=" Select * from duall";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_clearJoinActionPerformed

    private void alteraddTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alteraddTPActionPerformed
        try {
          String sql="Alter table top_player add "+newcolFieldTP.getText()+" varchar(30)";
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();
            
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_alteraddTPActionPerformed

    private void alterdelTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alterdelTPActionPerformed
       try {
          String sql="Alter table top_player drop column "+newcolFieldTP.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_alterdelTPActionPerformed

    private void alteraddBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alteraddBDActionPerformed
        try {
          String sql="Alter table ballondor add "+newcolFieldBD.getText()+" varchar(30)";
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_alteraddBDActionPerformed

    private void alterdelBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alterdelBDActionPerformed
       try {
          String sql="Alter table ballondor drop column "+newcolFieldBD.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_alterdelBDActionPerformed

    private void nestedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nestedActionPerformed
      try {
          String sql="Select player,goals from top_player where player in (select player from ballondor where votes>50000)";
            pst = conn.prepareStatement(sql);
            rs=pst.executeQuery();
            JoinTable.setModel(DbUtils.resultSetToTableModel(rs));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_nestedActionPerformed

    private void commitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commitActionPerformed
         try {
          String sql="Commit;";
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_commitActionPerformed

    private void savepointTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savepointTPActionPerformed
        try {
          String sql="Savepoint "+savepointFieldTP.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);


        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_savepointTPActionPerformed

    private void rollbackTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rollbackTPActionPerformed
        try {
          String sql="rollback to "+rollbackFieldTP.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}        // TODO add your handling code here:
    }//GEN-LAST:event_rollbackTPActionPerformed

    private void starttransactionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_starttransactionActionPerformed
         try {
          String sql="Start Transaction";
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);


        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}
    }//GEN-LAST:event_starttransactionActionPerformed

    private void savepointBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savepointBDActionPerformed
         try {
          String sql="Savepoint "+savepointFieldBD.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);


        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_savepointBDActionPerformed

    private void rollbackBDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rollbackBDActionPerformed
        try {
          String sql="rollback to "+rollbackFieldBD.getText();
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateBallondor();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}  // TODO add your handling code here:
    }//GEN-LAST:event_rollbackBDActionPerformed


    private void TopPlayerTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_TopPlayerTableMouseClicked

         DefaultTableModel model = (DefaultTableModel)TopPlayerTable.getModel();

        // get the selected row index
       int selectedRowIndex = TopPlayerTable.getSelectedRow();

        // set the selected row data into jtextfields
       playerFieldTP.setText(model.getValueAt(selectedRowIndex, 0).toString());
       goalsFieldTP.setText(model.getValueAt(selectedRowIndex, 1).toString());
       assistsFieldTP.setText(model.getValueAt(selectedRowIndex, 2).toString());
       passFieldTP.setText(model.getValueAt(selectedRowIndex, 3).toString());
       achievementsFieldTP.setText(model.getValueAt(selectedRowIndex, 4).toString());// TODO add your handling code here:
    }//GEN-LAST:event_TopPlayerTableMouseClicked

    private void deleteTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteTPActionPerformed

        try{

           String sql=("delete from top_player where player = '"+playerFieldTP.getText()+"'");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();

        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_deleteTPActionPerformed

    private void updateTPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateTPActionPerformed

         try{

           String sql=("update top_player set player = '"+playerFieldTP.getText()+"',goals="+goalsFieldTP.getText()+",assists="+assistsFieldTP.getText()+",pass="+passFieldTP.getText()+",achievements='"+achievementsFieldTP.getText()+"' where player='"+playerFieldTP.getText()+"';");
            pst = conn.prepareStatement(sql);
            pst.executeUpdate(sql);
            UpdateTopPlayer();

        }catch (SQLException ex) {
            JOptionPane.showMessageDialog(null,ex);}// TODO add your handling code here:
    }//GEN-LAST:event_updateTPActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel BallondorPanel;
    private javax.swing.JScrollPane BallondorScrollPanel;
    private javax.swing.JTable BallondorTable;
    private javax.swing.JButton ClearDual;
    private javax.swing.JPanel DualPanel;
    private javax.swing.JScrollPane DualScrollPane;
    private javax.swing.JTable DualTable;
    private javax.swing.JPanel JoinPanel;
    private javax.swing.JScrollPane JoinScrollPane;
    private javax.swing.JTable JoinTable;
    private javax.swing.JTabbedPane MainTabbedPanel;
    private javax.swing.JPanel TopPlayerPanel;
    private javax.swing.JScrollPane TopPlayerScrollPanel;
    private javax.swing.JTable TopPlayerTable;
    private javax.swing.JTextField achievementsFieldTP;
    private javax.swing.JLabel achievementsLabelTP;
    private javax.swing.JButton adddateDual;
    private javax.swing.JButton alteraddBD;
    private javax.swing.JButton alteraddTP;
    private javax.swing.JButton alterdelBD;
    private javax.swing.JButton alterdelTP;
    private javax.swing.JTextField assistsFieldBD;
    private javax.swing.JTextField assistsFieldTP;
    private javax.swing.JLabel assistsLabelBD;
    private javax.swing.JLabel assistsLabelTP;
    private javax.swing.JButton avgDual;
    private javax.swing.JButton cJoin;
    private javax.swing.JButton clearJoin;
    private javax.swing.JButton commit;
    private javax.swing.JButton concatDual;
    private javax.swing.JButton deleteBD;
    private javax.swing.JButton deleteTP;
    private javax.persistence.EntityManager entityManager;
    private javax.swing.JTextField goalsFieldBD;
    private javax.swing.JTextField goalsFieldTP;
    private javax.swing.JLabel goalsLabelBD;
    private javax.swing.JLabel goalsLabelTP;
    private javax.swing.JButton greatestDual;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JButton leastDual;
    private javax.swing.JButton lengthDual;
    private java.util.List<football.TopPlayer> list;
    private javax.swing.JButton loJoin;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JButton nJoin;
    private javax.swing.JButton nested;
    private javax.swing.JButton newBD;
    private javax.swing.JButton newTP;
    private javax.swing.JTextField newcolFieldBD;
    private javax.swing.JTextField newcolFieldTP;
    private javax.swing.JLabel newcolLabelBD;
    private javax.swing.JLabel newcolLabelTP;
    private javax.swing.JTextField passFieldTP;
    private javax.swing.JLabel passLabelTP;
    private javax.swing.JTextField playerFieldBD;
    private javax.swing.JTextField playerFieldTP;
    private javax.swing.JLabel playerLabelBD;
    private javax.swing.JLabel playerLabelTP;
    private javax.swing.JProgressBar progressBar;
    private javax.persistence.Query query;
    private javax.swing.JButton refreshBD;
    private javax.swing.JButton refreshTP;
    private javax.swing.JButton roJoin;
    private javax.swing.JButton rollbackBD;
    private javax.swing.JTextField rollbackFieldBD;
    private javax.swing.JTextField rollbackFieldTP;
    private javax.swing.JLabel rollbackLabelBD;
    private javax.swing.JLabel rollbackLabelTP;
    private javax.swing.JButton rollbackTP;
    private javax.swing.JButton savepointBD;
    private javax.swing.JTextField savepointFieldBD;
    private javax.swing.JTextField savepointFieldTP;
    private javax.swing.JLabel savepointLabelBD;
    private javax.swing.JLabel savepointLabelTP;
    private javax.swing.JButton savepointTP;
    private javax.swing.JButton searchBD;
    private javax.swing.JButton searchTP;
    private javax.swing.JButton starttransaction;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JTextField trophiesFieldBD;
    private javax.swing.JLabel trophiesLabelBD;
    private javax.swing.JButton updateBD;
    private javax.swing.JButton updateTP;
    private javax.swing.JTextField v1Field;
    private javax.swing.JLabel v1Label;
    private javax.swing.JTextField v2Field;
    private javax.swing.JLabel v2Label;
    private javax.swing.JTextField votesFieldBD;
    private javax.swing.JLabel votesLabelBD;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private JDialog aboutBox;

    private boolean saveNeeded;
}
