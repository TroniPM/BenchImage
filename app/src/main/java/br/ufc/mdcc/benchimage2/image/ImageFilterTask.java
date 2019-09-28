/*******************************************************************************
 * Copyright (C) 2014 Philipp B. Costa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package br.ufc.mdcc.benchimage2.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;

import br.ufc.mdcc.benchimage2.MainActivity;
import br.ufc.mdcc.benchimage2.dao.ResultDao;
import br.ufc.mdcc.benchimage2.dao.model.AppConfiguration;
import br.ufc.mdcc.benchimage2.dao.model.ResultImage;
import br.ufc.mdcc.mpos.MposFramework;
import br.ufc.mdcc.mpos.util.TaskResultAdapter;
import br.ufc.mdcc.util.ImageUtils;

/**
 * Asynctask for processing image...
 *
 * @author Philipp
 */
public final class ImageFilterTask extends AsyncTask<Void, String, ResultImage> {
    private final String clsName = ImageFilterTask.class.getName();
    private PowerManager.WakeLock wakeLock;

    private MainActivity mainActivity;
    private Context context;
    private Filter filter;
    private AppConfiguration config;
    private TaskResultAdapter<ResultImage> taskResult;

    private ResultDao dao;
    private ResultImage result = null;

    private long batteryBefore;

    public ImageFilterTask(MainActivity mainActivity, Filter filter, AppConfiguration config, TaskResultAdapter<ResultImage> taskResult, long batteryBefore) {
        this.mainActivity = mainActivity;
        this.context = mainActivity.getApplication();
        this.filter = filter;
        this.config = config;
        this.taskResult = taskResult;
        this.batteryBefore = batteryBefore;

        result = new ResultImage(config);
        dao = new ResultDao(context);
    }

    @Override
    protected void onPreExecute() {
        preventSleep();
    }

    @Override
    protected ResultImage doInBackground(Void... params) {
        try {
            if (config.getFilter().equals("Benchmark")) {
                Log.i(clsName, "Iniciou processo de Benchmark");
                benchmarkTask();
            } else if (config.getFilter().equals("Original")) {
                originalTask();
            } else if (config.getFilter().equals("Cartoonizer")) {
                cartoonizerTask();
            } else if (config.getFilter().equals("Sharpen")) {
                sharpenTask();
            } else {
                filterMapTask();
            }
            return result;
        } catch (InterruptedException e) {
            Log.w(clsName, e);
        } catch (IOException e) {
            Log.w(clsName, e);
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        taskResult.taskOnGoing(0, values[0]);
    }

    @Override
    protected void onPostExecute(ResultImage result) {
        wakeLock.release();
        taskResult.completedTask(result);
    }

    private String sizeToPath(String size) {
        if (size.equals("8MP")) {
            return "images/8mp/";
        } else if (size.equals("4MP")) {
            return "images/4mp/";
        } else if (size.equals("2MP")) {
            return "images/2mp/";
        } else if (size.equals("1MP")) {
            return "images/1mp/";
        } else if (size.equals("0.3MP")) {
            return "images/0_3mp/";
        }

        return null;
    }

    private String generatePhotoFileName() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getImage().replace(".jpg", "")).append("_").append(config.getFilter()).append("_");
        if (config.getSize().equals("0.7MP")) {
            sb.append("0_7mp.jpg");
        } else if (config.getSize().equals("0.3MP")) {
            sb.append("0_3mp.jpg");
        } else {
            sb.append(config.getSize()).append(".jpg");
        }
        return sb.toString();
    }

    private File saveResultOnStorage(byte data[]) throws IOException {
        String nome = generatePhotoFileName();

        Log.i(clsName, "getOutputDirectory(): " + config.getOutputDirectory());
        Log.i(clsName, "generatePhotoFileName(): " + nome);

        File file = new File(config.getOutputDirectory(), nome);
        OutputStream output = new FileOutputStream(file);
        output.write(data);
        output.flush();
        output.close();
        return file;
    }

    private void preventSleep() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PicFilter: CPU");
        wakeLock.acquire();
    }

    private void originalTask() throws IOException {
        long initialTime = System.currentTimeMillis();

        publishProgress("Carregando imagem");

        Bitmap image = decodeSampledBitmapFromResource(context, sizeToPath(config.getSize()) + config.getImage(), config.getSize());
        result.setTotalTime(System.currentTimeMillis() - initialTime);
        result.setBitmap(image);

        publishProgress("Imagem carregada!");
    }

    private void filterMapTask() throws IOException {
        long initialTime = System.currentTimeMillis();
        publishProgress("Aplicando " + config.getFilter());

        byte mapFilter[] = null;
        if (config.getFilter().equals("Red Ton")) {
            mapFilter = ImageUtils.streamToByteArray(context.getAssets().open("filters/map1.png"));
        } else if (config.getFilter().equals("Blue Ton")) {
            mapFilter = ImageUtils.streamToByteArray(context.getAssets().open("filters/map3.png"));
        } else if (config.getFilter().equals("Yellow Ton")) {
            mapFilter = ImageUtils.streamToByteArray(context.getAssets().open("filters/map2.png"));
        }

        byte image[] = ImageUtils.streamToByteArray(context.getAssets().open(sizeToPath(config.getSize()) + config.getImage()));
        byte imageResult[] = filter.mapTone(image, mapFilter);

        File fileSaved = saveResultOnStorage(imageResult);
        result.setTotalTime(System.currentTimeMillis() - initialTime);
        result.setBitmap(decodeSampledBitmapFromResource(context, new FileInputStream(fileSaved), config.getSize()));
        result.setRpcProfile(MposFramework.getInstance().getEndpointController().rpcProfile);

        result.setBatteryBefore(batteryBefore);
        result.setBatteryAfter(mainActivity.batteryLevel());

        dao.add(result);

        publishProgress("Terminou Processamento!");

        imageResult = null;
        image = null;
        mapFilter = null;
        System.gc();
    }

    private void sharpenTask() throws IOException {
        long initialTotalTime = System.currentTimeMillis();

        publishProgress("Aplicando Sharpen");

        byte image[] = ImageUtils.streamToByteArray(context.getAssets().open(sizeToPath(config.getSize()) + config.getImage()));
        double mask[][] = {{-1, -1, -1}, {-1, 9, -1}, {-1, -1, -1}};
        double factor = 1.0;
        double bias = 0.0;

        byte imageResult[] = filter.filterApply(image, mask, factor, bias);

        File fileSaved = saveResultOnStorage(imageResult);
        result.setTotalTime(System.currentTimeMillis() - initialTotalTime);
        result.setBitmap(decodeSampledBitmapFromResource(context, new FileInputStream(fileSaved), config.getSize()));
        result.setRpcProfile(MposFramework.getInstance().getEndpointController().rpcProfile);

        result.setBatteryBefore(batteryBefore);
        result.setBatteryAfter(mainActivity.batteryLevel());

        dao.add(result);

        publishProgress("Sharpen Completo!");

        imageResult = null;
        image = null;
        System.gc();
    }

    private void cartoonizerTask() throws IOException, InterruptedException {
        long initialTime = System.currentTimeMillis();

        publishProgress("Aplicando Cartoonizer");

        byte image[] = ImageUtils.streamToByteArray(context.getAssets().open(sizeToPath(config.getSize()) + config.getImage()));
        byte imageResult[] = filter.cartoonizerImage(image);

        File fileSaved = saveResultOnStorage(imageResult);
        result.setTotalTime(System.currentTimeMillis() - initialTime);
        result.setBitmap(decodeSampledBitmapFromResource(context, new FileInputStream(fileSaved), config.getSize()));
        result.setRpcProfile(MposFramework.getInstance().getEndpointController().rpcProfile);

        result.setBatteryBefore(batteryBefore);
        result.setBatteryAfter(mainActivity.batteryLevel());

        dao.add(result);

        publishProgress("Cartoonizer Completo!");

        imageResult = null;
        image = null;
        System.gc();
    }

    private void benchmarkTask() throws IOException, InterruptedException {
        long totalTime = 0L;

        File fileSaved = null;

        int count = 1;
        String sizes[] = {"8MP", "4MP", "2MP", "1MP", "0.3MP"};
        for (String size : sizes) {
            byte image[] = ImageUtils.streamToByteArray(context.getAssets().open(sizeToPath(size) + config.getImage()));

            config.setSize(size);
            for (int i = 0; i < 3; i++) {
                publishProgress("Benchmark [" + (count++) + "/15]");

                long initialTime = System.currentTimeMillis();
                byte imageResult[] = filter.cartoonizerImage(image);
                fileSaved = saveResultOnStorage(imageResult);

                ResultImage resultImage = new ResultImage(config);
                resultImage.setTotalTime(System.currentTimeMillis() - initialTime);
                resultImage.setRpcProfile(MposFramework.getInstance().getEndpointController().rpcProfile);

                dao.add(resultImage);
                totalTime += resultImage.getTotalTime();

                if (count != 16) {
                    imageResult = null;
                    System.gc();
                    Thread.sleep(750);
                }
            }
            image = null;
            System.gc();
        }

        result.setTotalTime(totalTime);
        result.setBitmap(decodeSampledBitmapFromResource(context, new FileInputStream(fileSaved), "0.3MP"));
        result.getConfig().setSize("Todos");

        result.setBatteryBefore(batteryBefore);
        result.setBatteryAfter(mainActivity.batteryLevel());

        dao.add(result);
        publishProgress("Benchmark Completo!");
    }

    private Bitmap decodeSampledBitmapFromResource(Context context, InputStream is, String size) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;

        if (size.equals("1MP") || size.equals("2MP")) {
            options.inSampleSize = 2;
        } else if (size.equals("4MP")) {
            options.inSampleSize = 4;
        } else if (size.equals("6MP")) {
            options.inSampleSize = 6;
        } else if (size.equals("8MP")) {
            options.inSampleSize = 8;
        } else {
            options.inSampleSize = 1;
        }

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(is, null, options);
    }

    private Bitmap decodeSampledBitmapFromResource(Context context, String path, String size) throws IOException {
        return decodeSampledBitmapFromResource(context, context.getAssets().open(path), size);
    }
}