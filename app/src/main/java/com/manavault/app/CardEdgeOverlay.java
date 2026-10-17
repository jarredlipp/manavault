package com.manavault.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;

/** Transparent live outer-corner and title-band overlay, independent of OCR. */
final class CardEdgeOverlay extends View {
    private final Paint border=new Paint(3),title=new Paint(3),corners=new Paint(3);
    private CardQuadDetector.Detection detection;
    private int imageWidth=1,imageHeight=1;
    private boolean stable;

    CardEdgeOverlay(Context context){
        super(context);
        border.setColor(Color.rgb(100,236,166));border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(5);
        title.setColor(Color.rgb(255,205,92));title.setStyle(Paint.Style.STROKE);title.setStrokeWidth(3);
        corners.setColor(Color.rgb(100,236,166));corners.setStyle(Paint.Style.FILL);
    }
    void update(CardQuadDetector.Detection result,int width,int height,boolean stable){
        this.detection=result;imageWidth=Math.max(1,width);imageHeight=Math.max(1,height);this.stable=stable;invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(detection==null)return;
        float scale=Math.max(getWidth()/(float)imageWidth,getHeight()/(float)imageHeight);
        float ox=(getWidth()-imageWidth*scale)/2,oy=(getHeight()-imageHeight*scale)/2;
        PointF[] p=detection.corners;
        PointF[] q=new PointF[4];for(int i=0;i<4;i++)q[i]=new PointF(p[i].x*scale+ox,p[i].y*scale+oy);
        border.setAlpha(stable?255:125);
        corners.setAlpha(stable?255:125);
        Path path=new Path();path.moveTo(q[0].x,q[0].y);for(int i=1;i<4;i++)path.lineTo(q[i].x,q[i].y);path.close();canvas.drawPath(path,border);
        for(PointF corner:q)canvas.drawCircle(corner.x,corner.y,8,corners);
        // The OCR title region is reconstructed from the card's own four corners.
        float horizontal=(float)Math.hypot(q[1].x-q[0].x,q[1].y-q[0].y);
        float vertical=(float)Math.hypot(q[3].x-q[0].x,q[3].y-q[0].y);
        PointF[] upright=horizontal>vertical?new PointF[]{q[3],q[0],q[1],q[2]}:q;
        PointF[] region={at(upright,.055f,.025f),at(upright,.84f,.025f),at(upright,.84f,.125f),at(upright,.055f,.125f)};
        Path titlePath=new Path();titlePath.moveTo(region[0].x,region[0].y);
        for(int i=1;i<4;i++)titlePath.lineTo(region[i].x,region[i].y);
        titlePath.close();canvas.drawPath(titlePath,title);
    }
    private static PointF at(PointF[] p,float u,float v){
        return new PointF((1-u)*(1-v)*p[0].x+u*(1-v)*p[1].x+u*v*p[2].x+(1-u)*v*p[3].x,
                (1-u)*(1-v)*p[0].y+u*(1-v)*p[1].y+u*v*p[2].y+(1-u)*v*p[3].y);
    }
}
