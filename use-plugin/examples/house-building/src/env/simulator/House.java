package simulator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;

import cartago.OPERATION;
import cartago.tools.GUIArtifact;

public class House extends GUIArtifact {

    HouseView view;

    @Override
    public void init(){
        if (java.awt.GraphicsEnvironment.isHeadless()) return;
        view = new HouseView();
        view.setVisible(true);
    }

    // Actions that simulate the building progress

    @OPERATION void prepareSite(){
        drawPart(new Site());
    }

    @OPERATION void layFloors(){
        await_time(1000);
        drawPart(new Floor());
    }

    @OPERATION void buildWalls(){
        await_time(500);
        drawPart(new Walls());
    }

    @OPERATION void buildRoof(){
        await_time(1000);
        drawPart(new Roof());
    }

    @OPERATION void fitDoors(){
        await_time(300);
        drawPart(new Doors());
    }

    @OPERATION void fitWindows(){
        await_time(300);
        drawPart(new Windows());
    }

    @OPERATION void paintExterior(){
        await_time(2000);
        drawPart(new ExteriorPainting());
    }

    @OPERATION void installPlumbing(){
        await_time(300);
        drawPart(new Plumbing());
    }

    @OPERATION void installElectricalSystem(){
        await_time(300);
        drawPart(new ElectricalSystem());
    }

    @OPERATION void paintInterior(){
        await_time(500);
        drawPart(new InteriorPainting());
    }


    private void drawPart(HousePart part) {
        if (view != null) view.addPart(part);
    }

    class HouseView extends JFrame {

        HousePanel           housePanel;
        ArrayList<HousePart> partsToDraw;

        public HouseView(){
            setTitle(" -- Home Sweet Home -- ");
            setSize(800,600);

            partsToDraw = new ArrayList<HousePart>();
            housePanel  = new HousePanel(this);
            setContentPane(housePanel);
        }

        public synchronized void addPart(HousePart part){
            partsToDraw.add(part);
            repaint();
        }

        public synchronized ArrayList<HousePart> getParts(){
            return (ArrayList<HousePart>)partsToDraw.clone();
        }
    }

    class HousePanel extends JPanel {

        HouseView view;

        public HousePanel(HouseView view){
            this.view = view;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

               g.setColor(Color.WHITE);
               Dimension size = getSize();
               g.fillRect(0, 0, size.width, size.height);

               for (HousePart part: view.getParts()){
                   part.draw(size,(Graphics2D)g);
               }
        }
    }
}
