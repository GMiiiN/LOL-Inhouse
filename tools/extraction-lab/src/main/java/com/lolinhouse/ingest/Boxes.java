package com.lolinhouse.ingest;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Gemini의 y 우선 정규화 좌표를 처리한다 */
public final class Boxes {
    private Boxes() {}
    /** 경계는 자르고 면적 없는 박스는 거부한다 */
    public static Rectangle pixels(int[] box,int width,int height) {
        if(box==null || box.length!=4 || width<=0 || height<=0
                || box[2]<=box[0] || box[3]<=box[1])
            throw new IllegalArgumentException("유효하지 않은 박스");
        int left=(int)Math.floor(Math.clamp(box[1],0,1000)*width/1000.0);
        int top=(int)Math.floor(Math.clamp(box[0],0,1000)*height/1000.0);
        int right=(int)Math.ceil(Math.clamp(box[3],0,1000)*width/1000.0);
        int bottom=(int)Math.ceil(Math.clamp(box[2],0,1000)*height/1000.0);
        if(right<=left || bottom<=top) throw new IllegalArgumentException("화면 밖 박스");
        return new Rectangle(left,top,right-left,bottom-top);
    }
    /** 픽셀 좌표로 원본을 자른다 */
    public static BufferedImage crop(BufferedImage image,int[] box) {
        Rectangle r=pixels(box,image.getWidth(),image.getHeight());
        return image.getSubimage(r.x,r.y,r.width,r.height);
    }
}
