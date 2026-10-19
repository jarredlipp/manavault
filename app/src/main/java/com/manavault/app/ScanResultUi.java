package com.manavault.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera-local scan feedback. No artwork downloads or UI rendering may block a save receipt.
 * Candidate pictures are merely aids to manual identification; tapping a printing still
 * sends its exact Scryfall ID to the existing verified-save pipeline.
 */
final class ScanResultUi {
    interface ChoiceHandler { void choose(String action,String printingId,String name); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(2);
    private static final LruCache<String,Bitmap> THUMBNAILS = new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private ScanResultUi() {}
    static int dp(Activity a,float size){return Math.round(size*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable panel(int color,int outline,int corner,Activity a){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,corner));
        d.setStroke(dp(a,1),outline);return d;
    }
    private static TextView text(Activity a,String value,int sp,int color){
        TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(sp);return t;
    }
    private static Bitmap photo(String path){
        if(path==null||path.isEmpty())return null;
        File f=new File(path);if(!f.isFile()||!f.getName().startsWith("native-scan-"))return null;
        BitmapFactory.Options b=new BitmapFactory.Options();b.inSampleSize=4;
        return BitmapFactory.decodeFile(path,b);
    }
    static View success(Activity a,FrameLayout root,JSONObject receipt,String photoPath,int controlsHeight){
        LinearLayout surface=new LinearLayout(a);surface.setOrientation(LinearLayout.HORIZONTAL);
        surface.setGravity(Gravity.CENTER_VERTICAL);surface.setPadding(dp(a,12),dp(a,11),dp(a,12),dp(a,11));
        surface.setBackground(panel(0xD923303C,0x996CD5B3,19,a));
        Bitmap captured=photo(photoPath);
        ImageView thumbnail=new ImageView(a);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(captured!=null)thumbnail.setImageBitmap(captured);
        else {thumbnail.setBackgroundColor(0xFF263649);thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);}
        // Always show the real captured card immediately. Replace it with
        // Scryfall's exact-printing thumbnail if a cached/network image loads;
        // never delay the receipt or success chime waiting on a thumbnail.
        JSONObject previewPrinting=receipt.optJSONObject("printing");
        if(previewPrinting!=null)referenceImage(a,thumbnail,previewPrinting.optString("image_url",""));
        LinearLayout.LayoutParams im=new LinearLayout.LayoutParams(dp(a,68),dp(a,89));im.rightMargin=dp(a,12);
        surface.addView(thumbnail,im);
        LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);
        JSONObject p=receipt.optJSONObject("printing");
        boolean verified="verified".equals(receipt.optString("status"));
        TextView heading=text(a,verified?"✓  Saved to collection":"✓  Saved · printing unverified",14,0xFF9CEFC7);
        heading.setTypeface(null,1);content.addView(heading);
        TextView name=text(a,receipt.optString("name","Card"),17,Color.WHITE);name.setTypeface(null,1);
        name.setMaxLines(2);content.addView(name);
        String edition=p==null?"Printing not yet verified":
            (p.optString("set_name",p.optString("set","Unknown set"))+" · #"+p.optString("collector_number","?"));
        content.addView(text(a,edition,12,0xFFDFE9EF));
        if(p!=null){
            String details=(p.optBoolean("foil",false)?"Foil":"Normal")+" · "+p.optString("condition","NM")+
                " · "+p.optString("lang","EN").toUpperCase(Locale.ROOT)+" · ×"+Math.max(1,p.optInt("qty",1));
            if(!p.optString("rarity","").isEmpty())details+=" · "+p.optString("rarity","");
            double price=p.optDouble("price",Double.NaN);
            if(!Double.isNaN(price) && price>0)details+=" · $"+String.format(Locale.US,"%.2f",price);
            content.addView(text(a,details,12,0xFFA4E7D4));
        }
        surface.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text(a,"×",22,0xFFE8F0F5);close.setGravity(Gravity.TOP|Gravity.RIGHT);
        close.setPadding(dp(a,8),0,0,0);surface.addView(close);
        close.setOnClickListener(v->root.removeView(surface));
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        pos.setMargins(dp(a,13),0,dp(a,13),Math.max(controlsHeight,dp(a,72))+dp(a,14));
        root.addView(surface,pos);
        MAIN.postDelayed(()->{if(surface.getParent()==root)root.removeView(surface);},1450);
        return surface;
    }
    private static final class Candidate {
        final String action,id,name,label,url;
        Candidate(String action,String id,String name,String label,String url){
            this.action=action;this.id=id;this.name=name;this.label=label;this.url=url;
        }
    }
    private static boolean trustedImage(String url){
        try{
            URL u=new URL(url);String host=u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getProtocol()) &&
                   ("cards.scryfall.io".equals(host)||"scryfall.io".equals(host));
        }catch(Exception ignored){return false;}
    }
    private static void referenceImage(Activity a,ImageView target,String url){
        if(!trustedImage(url))return;
        target.setTag(url);
        Bitmap ready=THUMBNAILS.get(url);
        if(ready!=null){target.setImageBitmap(ready);return;}
        IMAGES.execute(()->{
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(1400);conn.setReadTimeout(1600);
                conn.setInstanceFollowRedirects(false);
                if(conn.getResponseCode()!=200)return;
                if(conn.getContentLengthLong()>512000)return;
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
                Bitmap picture;
                try(java.io.InputStream stream=conn.getInputStream()){picture=BitmapFactory.decodeStream(stream,null,opts);}
                if(picture==null)return;
                THUMBNAILS.put(url,picture);
                a.runOnUiThread(()->{
                    if(!a.isFinishing() && url.equals(target.getTag()))target.setImageBitmap(picture);
                });
            }catch(Exception ignored){/* Images are optional. The text picker is always usable. */}
            finally{if(conn!=null)conn.disconnect();}
        });
    }
    static Dialog review(Activity a,JSONObject payload,ChoiceHandler callback){
        final String name=payload.optString("name","");
        final JSONArray printings=payload.optJSONArray("choices");
        final JSONArray suggestions=payload.optJSONArray("suggestions");
        final ArrayList<Candidate> candidates=new ArrayList<>();
        if(printings!=null)for(int i=0;i<printings.length();i++){
            JSONObject p=printings.optJSONObject(i);if(p==null||p.optString("id","").isEmpty())continue;
            candidates.add(new Candidate("printing",p.optString("id"),"",
                p.optString("label","Unknown printing"),p.optString("image_url","")));
        }
        if(candidates.isEmpty()&&suggestions!=null)for(int i=0;i<suggestions.length();i++){
            JSONObject p=suggestions.optJSONObject(i);if(p==null||p.optString("name","").isEmpty())continue;
            candidates.add(new Candidate("correct-name","",p.optString("name"),
                p.optString("label",p.optString("name")),p.optString("image_url","")));
        }
        Dialog dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,14));
        root.setBackground(panel(0xFFFFFFFF,0xFFDBE3EB,24,a));
        TextView title=text(a,candidates.isEmpty()?"Identify this card":
            printings!=null&&printings.length()>0?"Select the correct printing":"Closest card matches",20,0xFF17202B);
        title.setTypeface(null,1);root.addView(title);
        if(!name.isEmpty())root.addView(text(a,name,16,0xFF394A5B));
        String reason=payload.optString("reason","Identification needs confirmation");
        TextView explanation=text(a,reason,12,0xFF5B6674);explanation.setMaxLines(3);
        LinearLayout.LayoutParams reasonParams=new LinearLayout.LayoutParams(-1,-2);
        reasonParams.topMargin=dp(a,5);root.addView(explanation,reasonParams);
        final EditText search=new EditText(a);search.setSingleLine(true);search.setTextSize(15);
        search.setHint(candidates.isEmpty()?"Enter a card name":"Filter by card, set or collector number");
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(a,52)));
        final ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);
        final GridLayout grid=new GridLayout(a);grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER);
        Button retry=new Button(a);retry.setText("Retry");retry.setAllCaps(false);
        Button skip=new Button(a);skip.setText("Skip");skip.setAllCaps(false);
        footer.addView(retry,new LinearLayout.LayoutParams(0,dp(a,48),1));
        footer.addView(skip,new LinearLayout.LayoutParams(0,dp(a,48),1));
        if(payload.optBoolean("allow_unverified",false)){
            Button unverified=new Button(a);unverified.setText("Keep unverified");unverified.setAllCaps(false);
            unverified.setTextSize(11);footer.addView(unverified,new LinearLayout.LayoutParams(0,dp(a,48),1.5f));
            unverified.setOnClickListener(v->{callback.choose("unverified","","");dialog.dismiss();});
        }
        root.addView(footer);
        retry.setOnClickListener(v->{callback.choose("retry","","");dialog.dismiss();});
        skip.setOnClickListener(v->{callback.choose("skip","","");dialog.dismiss();});
        final Button manual=new Button(a);manual.setText("Search / correct card name");manual.setAllCaps(false);
        root.addView(manual,new LinearLayout.LayoutParams(-1,dp(a,48)));
        manual.setOnClickListener(v->{
            String correction=search.getText().toString().trim();
            if(correction.isEmpty()){search.setError("Enter a card name");return;}
            callback.choose("correct-name","",correction);dialog.dismiss();
        });
        final int[] shown={0};
        final int[] visibleLimit={8};
        final Button showMore=new Button(a);showMore.setText("Show more matches");
        showMore.setAllCaps(false);
        root.addView(showMore,root.indexOfChild(footer),new LinearLayout.LayoutParams(-1,dp(a,44)));
        final Runnable repaint=()->{
            grid.removeAllViews();shown[0]=0;
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int max=visibleLimit[0];
            for(Candidate c:candidates){
                if(!c.label.toLowerCase(Locale.ROOT).contains(query))continue;
                if(shown[0]++>=max)continue;
                LinearLayout tile=new LinearLayout(a);tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(dp(a,7),dp(a,7),dp(a,7),dp(a,7));
                tile.setBackground(panel(0xFFF5F7FA,0xFFD5DFE9,12,a));
                ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setBackgroundColor(0xFFE0E5EB);
                image.setImageResource(android.R.drawable.ic_menu_gallery);
                tile.addView(image,new LinearLayout.LayoutParams(-1,dp(a,140)));
                referenceImage(a,image,c.url);
                TextView label=text(a,c.label,12,0xFF1D2935);label.setMaxLines(4);
                LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,dp(a,66));
                labelParams.topMargin=dp(a,6);tile.addView(label,labelParams);
                GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
                cell.width=(a.getResources().getDisplayMetrics().widthPixels-dp(a,50))/2;
                cell.height=dp(a,226);
                cell.setMargins(dp(a,3),dp(a,3),dp(a,3),dp(a,3));
                grid.addView(tile,cell);
                tile.setOnClickListener(v->{callback.choose(c.action,c.id,c.name);dialog.dismiss();});
            }
            showMore.setVisibility(shown[0]>max?View.VISIBLE:View.GONE);
            if(grid.getChildCount()==0){
                TextView empty=text(a,"No matching candidates. Search by a different name.",14,0xFF435063);
                grid.addView(empty);
            }
        };
        final Runnable update=repaint;
        showMore.setOnClickListener(v->{visibleLimit[0]+=8;update.run();});
        repaint.run();
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){repaint.run();}
            public void afterTextChanged(Editable e){}
        });
        dialog.setContentView(root);
        dialog.setCancelable(false);
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs=window.getAttributes();
            attrs.width=WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height=(int)(a.getResources().getDisplayMetrics().heightPixels*0.79f);
            attrs.gravity=Gravity.BOTTOM;
            attrs.dimAmount=0.23f;
            window.setAttributes(attrs);
        }
        return dialog;
    }
}
