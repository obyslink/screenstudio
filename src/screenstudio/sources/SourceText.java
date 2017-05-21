/*
 * Copyright (C) 2017 patrick
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package screenstudio.sources;

import screenstudio.gui.LabelText;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import static screenstudio.targets.Layout.SourceType.LabelText;

/**
 *
 * @author patrick
 */
public class SourceText extends Source implements Runnable {

    private final BufferedImage mImage;
    private String mText;
    private String mRawText;
    private boolean mStopMe = false;
    private final long mStartTimeStamp = System.currentTimeMillis();
    private boolean mOnChangeOnly = false;
    private boolean mOneLiner = false;
    private long mReloadTime = 1000;
    private long mLastReloadTime = System.currentTimeMillis();
    private final byte[] mData;
    private int mBackgroundArea;
    private Font mFont;
    private byte[] mBuffer;
    private boolean mScrollVertical = false;
    private boolean mScrollHorizontal = false;
    private boolean mTypeWriterMode = false;

    private final DateFormat formatDate = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
    private final DateFormat formatTime = DateFormat.getTimeInstance(DateFormat.LONG, Locale.getDefault());

    public SourceText(List<screenstudio.targets.Source.View> views, Rectangle size, Color fg, Color bg, Color areabg, Color rect, String text, Font f, String id) {
        super(views, 0, id, BufferedImage.TYPE_4BYTE_ABGR);
        mImage = new BufferedImage(size.width, size.height, mImageType);
        mBounds = size;
        mData = ((DataBufferByte) mImage.getRaster().getDataBuffer()).getData();
        mBuffer = new byte[mData.length];
        System.arraycopy(mData, 0, mBuffer, 0, mBuffer.length);
        mRawText = text;
        mText = updateWithTextTags(text);
        mForeground = fg.getRGB();
        mBackground = bg.getRGB();
        mBackgroundArea = areabg.getRGB();
        mFont = f;
    }
    public SourceText(List<screenstudio.targets.Source.View> views,LabelText text){
        super(views, 0, "", BufferedImage.TYPE_4BYTE_ABGR);
        mImage = new BufferedImage(views.get(0).Width, views.get(0).Height, mImageType);
        mData = ((DataBufferByte) mImage.getRaster().getDataBuffer()).getData();
        mBuffer = new byte[mData.length];
        System.arraycopy(mData, 0, mBuffer, 0, mBuffer.length);
        mRawText = text.getText();
        mText = updateWithTextTags(mRawText);
        mForeground = text.getForegroundColor();
        mBackground = text.getBackgroundColor();
        mBackgroundArea = text.getBackgroundAreaColor();
        mFont = new Font(text.getFontName(),Font.PLAIN,text.getFontSize());
    }

    @Override
    protected void getData(byte[] buffer) throws IOException {
        System.arraycopy(mBuffer, 0, buffer, 0, buffer.length);
    }

    @Override
    protected void initStream() throws IOException {
        mStopMe = false;
        new Thread(this).start();
    }

    @Override
    protected void disposeStream() throws IOException {
        mStopMe = true;
    }

    public void setText(String text) {
        mRawText = text;
        mText = updateWithTextTags(text);
    }

    public void setFont(Font f) {
        mFont = f;
    }

    public void setForeground(int color) {
        mForeground = color;
    }

    public void setBackground(int color) {
        mBackground = color;
    }

    public void setBackgroundArea(int color) {
        mBackgroundArea = color;
    }

    @Override
    public void run() {
        Graphics2D g = mImage.createGraphics();
        int xRelative = 0;
        int yRelative = 0;
        int lastIndex = 1;
        long lastIndexTime = System.currentTimeMillis();
        while (!mStopMe) {
            g.setFont(mFont);
            if (System.currentTimeMillis() - mLastReloadTime > mReloadTime) {
                mText = updateWithTextTags(mRawText);
                mLastReloadTime = System.currentTimeMillis();
            }
            String content = replaceTags(mText);
            if (mTypeWriterMode) {
                if (System.currentTimeMillis() - lastIndexTime > 100) {
                    lastIndex++;
                    if (lastIndex >= content.length()) {
                        lastIndex = 0;
                        lastIndexTime = System.currentTimeMillis() + 5000;
                    } else {
                        lastIndexTime = System.currentTimeMillis();
                    }
                }
                if (lastIndex > 0) {
                    content = content.substring(0, lastIndex);
                }
            }
            java.util.Arrays.fill(mData, (byte) 0);
            String[] lines = content.split("\n");
            g.setColor(new Color(mBackgroundArea, true));
            g.fillRect(0, 0, mImage.getWidth(), mImage.getHeight());
            if (mScrollHorizontal) {
                xRelative -= 2;
            } else {
                xRelative = 0;
            }
            if (mScrollVertical) {
                yRelative -= 2;
            } else {
                yRelative = 0;
            }
            int x = xRelative;
            int y = g.getFontMetrics().getStringBounds(lines[0], g).getBounds().height + yRelative;
            for (String line : lines) {
                line = line.trim();
                Rectangle2D size = g.getFontMetrics().getStringBounds(line, g);
                g.setColor(new Color(mBackground, true));
                g.drawString(line, x, y);
                g.drawString(line, x - 1, y - 1);
                g.setColor(new Color(mForeground, true));
                g.drawString(line, x - 2, y - 2);
                if (mScrollHorizontal) {
                    x += size.getWidth() + 5;
                } else {
                    y += size.getHeight();
                }
            }
            if (mScrollVertical && y < 0) {
                yRelative = mImage.getHeight();
            }
            if (mScrollHorizontal && x < 0) {
                xRelative = mImage.getWidth();
            }
            mBuffer = new byte[mData.length];
            System.arraycopy(mData, 0, mBuffer, 0, mBuffer.length);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(SourceText.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        g.dispose();
    }

    private String replaceTags(String text) {
        String retValue = text + "";

        int index = retValue.indexOf("file:///");
        while (index != -1) {
            int toIndex = retValue.indexOf(";", index);
            if (toIndex == -1) {
                toIndex = retValue.indexOf(" ", index);
            }
            if (toIndex == -1) {
                toIndex = retValue.indexOf("\n", index);
            }
            if (toIndex == -1) {
                toIndex = retValue.length() - 1;
            }
            if (toIndex != -1) {
                String file = retValue.substring(index, toIndex + 1);
                retValue = retValue.replaceAll(file, getFileContent(new File(retValue.substring(index + 6, toIndex))));
                index = retValue.indexOf("file:///", toIndex + 1);
            } else {
                index = retValue.indexOf("file:///", index + 1);
            }
        }
        retValue = retValue.replaceAll("@CURRENTDATE", formatDate.format(new Date()));
        retValue = retValue.replaceAll("@CURRENTTIME", formatTime.format(new Date()));
        retValue = retValue.replaceAll("@RECORDINGTIME", ((System.currentTimeMillis() - mStartTimeStamp) / 1000 / 60) + " min");
        retValue = retValue.replaceAll("@STARTTIME", formatTime.format(new Date(mStartTimeStamp)));
        return retValue;
    }

    private String getFileContent(File file) {
        String text = "";
        if (file.exists()) {
            try {
                InputStream in = file.toURI().toURL().openStream();
                byte[] data = new byte[(int) file.length()];
                in.read(data);
                in.close();
                text = new String(data).trim();

            } catch (IOException ex) {
                Logger.getLogger(SourceLabel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return text;
    }

    private String updateWithTextTags(String text) {
        String retValue = text + "";
        int index = retValue.indexOf("@UPDATE");
        if (index != -1) {
            int toIndex = retValue.indexOf("MIN@");
            if (toIndex == -1) {
                toIndex = retValue.indexOf("SEC@");
            }
            if (toIndex != -1) {
                String update = retValue.substring(index, toIndex + 4);
                try {
                    String intValue = update.replaceAll("@UPDATE", "").replaceAll(" ", "").replaceAll("MIN@", "").replaceAll("SEC@", "");
                    int value = new Integer(intValue.trim());
                    if (update.endsWith("MIN@")) {
                        value = value * 60000;
                    } else if (update.endsWith("SEC@")) {
                        value = value * 1000;
                    }
                    mReloadTime = value;
                    retValue = retValue.replaceAll(update, "");
                } catch (Exception ex) {
                    System.err.println("Parsing update value failed:" + ex.getMessage());
                }
            }
        }
        mOnChangeOnly = false;
        index = retValue.indexOf("@ONCHANGEONLY");
        if (index != -1) {
            setAlpha(0);
            mOnChangeOnly = true;
            retValue = retValue.replaceAll("@ONCHANGEONLY", "");
        }
        mOneLiner = false;
        index = retValue.indexOf("@ONELINER");
        if (index != -1) {
            mOneLiner = true;
            retValue = retValue.replaceAll("@ONELINER", "");
        }
        mScrollVertical = false;
        mScrollHorizontal = false;
        index = retValue.indexOf("@SCROLLVERTICAL");
        if (index != -1) {
            mScrollVertical = true;
            retValue = retValue.replaceAll("@SCROLLVERTICAL", "");
        }
        index = retValue.indexOf("@SCROLLHORIZONTAL");
        if (index != -1) {
            mScrollHorizontal = true;
            retValue = retValue.replaceAll("@SCROLLHORIZONTAL", "");
        }
        index = retValue.indexOf("@TYPEWRITER");
        if (index != -1) {
            mTypeWriterMode = true;
            retValue = retValue.replaceAll("@TYPEWRITER", "");
        }
        return retValue.trim();
    }

}