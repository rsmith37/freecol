
package net.sf.freecol.client.gui.panel;


import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import net.sf.freecol.client.gui.Canvas;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;


/**
 * This label holds Goods data in addition to the JLabel data, which makes
 * it ideal to use for drag and drop purposes.
 */
public final class GoodsLabel extends JLabel {//implements ActionListener {
    public static final String  COPYRIGHT = "Copyright (C) 2003-2005 The FreeCol Team";
    public static final String  LICENSE = "http://www.gnu.org/licenses/gpl.html";
    public static final String  REVISION = "$Revision$";

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(GoodsLabel.class.getName());

    private final Goods goods;
    private final Canvas parent;
    private boolean selected;
    private boolean partialChosen;

    /**
    * Initializes this JLabel with the given goods data.
    * @param goods The Goods that this JLabel will visually represent.
    * @param parent The parent that knows more than we do.
    */
    public GoodsLabel(Goods goods, Canvas parent) {
        super(parent.getImageProvider().getGoodsImageIcon(goods.getType()));
        this.goods = goods;
        setToolTipText(goods.getName());
        this.parent = parent;
        selected = false;
        partialChosen = false;
    }

    
    /**
    * Initializes this JLabel with the given goods data.
    * 
    * @param goods The Goods that this JLabel will visually represent.
    * @param parent The parent that knows more than we do.
    * @param isSmall A smaller picture will be used if <code>true</code>.
    */
    public GoodsLabel(Goods goods, Canvas parent, boolean isSmall) {
        this(goods, parent);
        setSmall(true);
    }


    public boolean isPartialChosen() {
        return partialChosen;
    }
    
    
    public void setPartialChosen(boolean partialChosen) {
        this.partialChosen = partialChosen;
    }


    /**
     * Returns the parent Canvas object.
     * @return This UnitLabel's Canvas.
     */
    public Canvas getCanvas() {
        return parent;
    }


    /**
    * Returns this GoodsLabel's goods data.
    * @return This GoodsLabel's goods data.
    */
    public Goods getGoods() {
        return goods;
    }

    
    /**
    * Sets whether or not this goods should be selected.
    * @param b Whether or not this goods should be selected.
    */
    public void setSelected(boolean b) {
        selected = b;
    }


    /**
    * Sets that this <code>GoodsLabel</code> should be small.
    * @param isSmall A smaller picture will be used if <code>true</code>.
    */
    public void setSmall(boolean isSmall) {
        if (isSmall) {
            ImageIcon imageIcon = (parent.getImageProvider().getGoodsImageIcon(goods.getType()));
            setIcon(new ImageIcon(imageIcon.getImage().getScaledInstance(imageIcon.getIconWidth() / 2, imageIcon.getIconHeight() / 2, Image.SCALE_DEFAULT)));
        } else {
            setIcon(parent.getImageProvider().getGoodsImageIcon(goods.getType()));
        }
    }


    /**
    * Paints this GoodsLabel.
    * @param g The graphics context in which to do the painting.
    */
    public void paintComponent(Graphics g) {
        Player player = null;
        Location location = goods.getLocation();
        
        if (location instanceof Ownable) {
            player = ((Ownable) location).getOwner();
        }
        if (player == null ||
            goods.getType() >= Goods.NUMBER_OF_TYPES ||
            player.canTrade(goods) || 
            (location instanceof Colony &&
             player.getGameOptions().getBoolean(GameOptions.CUSTOM_IGNORE_BOYCOTT) &&
             ((Colony) location).getBuilding(Building.CUSTOM_HOUSE).getLevel() != Building.NOT_BUILT)) {
            setToolTipText(goods.getName());
            setEnabled(true);
        } else {
            setToolTipText(goods.getName(false));
            setEnabled(false);
        }

        if (goods.getType() != Goods.FOOD && location instanceof Colony 
                && ((Colony) location).getWarehouseCapacity() < goods.getAmount()) {
            setForeground(Color.RED);
        } else if (location instanceof Colony && location != null &&
                   goods.getType() < Goods.NUMBER_OF_TYPES &&
                   ((Colony) location).getExports(goods)) {
            setForeground(Color.GREEN);
        } else if (goods.getAmount() == 0) {
            setForeground(Color.GRAY);
        } else {
            setForeground(Color.BLACK);
        }

        super.setText(String.valueOf(goods.getAmount()));
        super.paintComponent(g);
    }

    /**
    * Analyzes an event and calls the right external methods to take
    * care of the user's request.
    * @param event The incoming action event
    */
    /* isn't actually used
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();
        try {
                switch (Integer.valueOf(command).intValue()) {
                    default:
                        logger.warning("Invalid action");
                }
                setIcon(parent.getImageProvider().getGoodsImageIcon(goods.getType()));
                repaint(0, 0, getWidth(), getHeight());
                
                // TODO: Refresh the gold label when goods have prices.
                //goldLabel.repaint(0, 0, goldLabel.getWidth(), goldLabel.getHeight());
        }
        catch (NumberFormatException e) {
            logger.warning("Invalid action number");
        }
    }
    */
}
