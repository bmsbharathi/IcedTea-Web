/* AWTHelper.java
Copyright (C) 2012 Red Hat, Inc.

This file is part of IcedTea.

IcedTea is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 2.

IcedTea is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea; see the file COPYING.  If not, write to
the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
 */
package net.adoptopenjdk.icedteaweb.testing.awt;

import net.adoptopenjdk.icedteaweb.testing.closinglisteners.Rule;
import net.adoptopenjdk.icedteaweb.testing.closinglisteners.RulesFolowingClosingListener;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

public abstract class AWTHelper extends RulesFolowingClosingListener implements Runnable{

    //attributes possibly set by user
    private String initStr = null;
    private int appletHeight;
    private int appletWidth;
    private final int tryKTimes = DEFAULT_K;

    //other 
    protected final StringBuilder sb = new StringBuilder();
    private boolean actionStarted = false;
    private Rectangle actionArea;
    private BufferedImage screenshot;
    private Robot robot;
    private boolean appletFound = false;
    private boolean markerGiven = false; //impossible to find the applet if marker not given
    private boolean appletDimensionGiven = false;
    private final int defaultWaitForApplet = 1000;
    
    //default number of times the screen is captured and the applet is searched for
    //in the screenshot
    public static final int DEFAULT_K = 3;
   
    
    //several constructors
    /**
     * the minimal constructor - use:
     *  - if we do not want to find the bounds of applet area first
     *  - searching for buttons and other components is then done in the whole
     *    screen, confusion with other icons on display is then possible
     *  - less effective, deprecated (better bound the area first) 
     */
    @Deprecated
    public AWTHelper() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("AWTHelper could not create its Robot instance.",e);
        }
    }
    
    /**
     * the minimal constructor with initStr - use:
     *  - we want to know from stdout that the applet (or sth else) is ready
     *  - if we do not want to find the bounds of applet area first
     *  - searching for buttons and other components is then done in the whole
     *    screen, confusion with other icons on display is then possible
     *  - less effective, deprecated (better bound the area first) 
     */
    @Deprecated
    public AWTHelper(String initStr){
        this();
        
        this.initStr = initStr;
    }

    /**
     * override of method charReaded (from RulesFolowingClosingListener)
     * 
     * waiting for the applet, when applet is ready run action thread
     * (if initStr==null, do not check and do not call run)
     * 
     * when all the wanted strings are in the stdout, applet can be closed
     * 
     * @param ch 
     */
    @Override
    public void charReaded(char ch) {
        sb.append(ch);
        //is applet ready to start clicking?
        //check and run applet only if initStr is not null
        if ((initStr != null) && !actionStarted && appletIsReady(sb.toString())) {
            try{
                actionStarted = true; 
                this.findAndActivateApplet();
                this.run();
            } catch (ComponentNotFoundException e1) {
                throw new RuntimeException("AWTHelper problems finding applet.",e1);
            } catch (AWTFrameworkException e2){
                throw new RuntimeException("AWTHelper problems with unset attributes.",e2);
            }
        }
        //is all the wanted output in stdout?
        super.charReaded(ch);
    }
    


    /**
     * implementation of AWTHelper should implement the run method
     */
    public abstract void run();


    /**
     * method getInitStrAsRule returns the initStr in the form
     * of Contains rule that can be evaluated on a string 
     * 
     * @return
     */
    public Rule<String, String> getInitStrAsRule(){
    	if( initStr != null ){
            return new ContainsRule(this.initStr);
    	}else{
    		return new Rule<String, String>(){

				@Override
				public void setRule(String rule) {
				}

				@Override
				public boolean evaluate(String upon) {
					return true;
				}

    			
    		} ;
    	}
    }
    
    //boolean controls getters
    protected boolean appletIsReady(String content) {
        return this.getInitStrAsRule().evaluate(content);
    }



    //creating screenshots, searching for applet
    /**
     * method captureScreenAndFindAppletByIcon
     * 1. checks that all needed attributes of AWTHelper are given
     *    (marker, its position and applet width and height)
     * 2. captures screen, 
     * 3. finds the rectangle where applet is and saves it to the attribute
     *    actionArea 
     * 4. sets screenCapture indicator to true (after tryKTimes unsuccessfull
     *    tries an exception "ComponentNotFound" will be raised)
     * 
     * @throws ComponentNotFoundException  
     * @throws AWTFrameworkException 
     * @throws AWTFrameworkException 
     */
    public void captureScreenAndFindAppletByIcon() throws ComponentNotFoundException, AWTFrameworkException {
        if(!appletDimensionGiven || !markerGiven){
            throw new AWTFrameworkException("AWTFramework cannot find applet without dimension or marker!");
        }
        captureScreenAndFindAppletByIconTryKTimes(null, null, appletWidth, appletHeight, tryKTimes);
    }

    /**
     ** method captureScreenAndFindAppletByIcon
     * 1. captures screen, 
     * 2. finds the rectangle where applet is and saves it to the attribute
     *    actionArea 
     * 3. sets screenCapture indicator to true (after tryKTimes unsuccessfull
     *    tries an exception "ComponentNotFound" will be raised) 
     * 
     * @param icon
     * @param iconPosition
     * @param width
     * @param height
     * @param K
     * @throws ComponentNotFoundException
     */
    public void captureScreenAndFindAppletByIconTryKTimes(BufferedImage icon, Point iconPosition, int width, int height, int K) throws ComponentNotFoundException {
  
        int count = 0;
        appletFound = false;
        while ((count < K) && !appletFound) {
            robot.delay(defaultWaitForApplet);
            try {
                screenshot = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                initialiseOnScreenshot(icon, iconPosition, width, height, screenshot);
            } catch (ComponentNotFoundException ex) {
                //keeping silent and try more-times
            }
            count++;
        }

        if (ImageSeeker.isRectangleValid(actionArea)) {
            appletFound = true;
        } else {
            throw new ComponentNotFoundException("Object not found in the screenshot!");
        }

    }
    
    public void initialiseOnScreenshot(BufferedImage icon, Point iconPosition, int width, int height, BufferedImage screenshot) throws ComponentNotFoundException {
        Rectangle r = ComponentFinder.findWindowByIcon(icon, iconPosition, width, height, screenshot);
        initialiseOnScreenshotAndArea(screenshot, r);
        
    }
    
    public void initialiseOnScreenshotAndArea(BufferedImage screenshot, Rectangle actionArea) throws ComponentNotFoundException {
        this.screenshot = screenshot;
        this.actionArea = actionArea;
        if (ImageSeeker.isRectangleValid(actionArea)) {
            appletFound = true;
        } else {
            throw new ComponentNotFoundException("set invalid area!");
        }
    }


    
    /**
     * method findAndActivateApplet finds the applet by icon 
     * and clicks in the middle of applet area
     * 
     * @throws ComponentNotFoundException (applet not found) 
     * @throws AWTFrameworkException 
     */
    public void findAndActivateApplet() throws ComponentNotFoundException, AWTFrameworkException
    {
        captureScreenAndFindAppletByIcon();
        clickInTheMiddleOfApplet();
    }


    //methods for clicking and typing 
    /**
     * method clickInTheMiddleOfApplet focuses the applet by clicking in the
     * middle of its location rectangle
     */
    public void clickInTheMiddleOfApplet() {
        MouseActions.clickInside(this.actionArea, this.robot);
    }
    

 }
