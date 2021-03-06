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
package br.ufc.mdcc.benchimage2;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import br.ufc.mdcc.benchimage2.dao.model.AppConfiguration;
import br.ufc.mdcc.benchimage2.dao.model.ResultImage;
import br.ufc.mdcc.benchimage2.image.Filter;
import br.ufc.mdcc.benchimage2.image.CloudletFilter;
import br.ufc.mdcc.benchimage2.image.InternetFilter;
import br.ufc.mdcc.benchimage2.image.ImageFilter;
import br.ufc.mdcc.benchimage2.image.ImageFilterTask;
import br.ufc.mdcc.benchimage2.util.ExportData;
import br.ufc.mdcc.mpos.MposFramework;
import br.ufc.mdcc.mpos.config.Inject;
import br.ufc.mdcc.mpos.config.MposConfig;
import br.ufc.mdcc.mpos.util.TaskResultAdapter;

/**
 * @author Philipp
 */
@MposConfig(endpointSecondary = "18.228.151.23")
public final class MainActivity extends Activity {
    private final String clsName = MainActivity.class.getName();

    private Filter filterLocal = new ImageFilter();

    @Inject(ImageFilter.class)
    private CloudletFilter cloudletFilter;

    @Inject(ImageFilter.class)
    private InternetFilter internetFilter;

    private AppConfiguration config;
    private String photoName;
    private long vmSize = 0L;

    private boolean quit;

    public static long tempoTotalUI = 0;
    public static boolean semaforo = true;

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void requestAllPermissionsAtOnce() {
        // The request code used in ActivityCompat.requestPermissions()
        // and returned in the Activity's onRequestPermissionsResult()
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
        };

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestAllPermissionsAtOnce();

        setContentView(R.layout.activity_main);

        quit = false;
        MposFramework.getInstance().start(this);

        config = new AppConfiguration();

        configureSpinner();
        getConfigFromSpinner();

        configureButton();
        configureStatusView("Status: Sem Atividade");

        createDirOutput();
        processImage(config, filterLocal, cloudletFilter, internetFilter, 0);

        Log.i(clsName, "Iniciou PicFilter");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (quit) {
            MposFramework.getInstance().stop();
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        switch (item.getItemId()) {
            case R.id.menu_action_export:
                alertDialogBuilder.setTitle("Exportar Resultados");
                alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
                alertDialogBuilder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        new ExportData(MainActivity.this, "benchimage2_data.csv").execute();
                    }
                });
                alertDialogBuilder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                alertDialogBuilder.setMessage("Deseja exportar resultados?");
                alertDialogBuilder.create().show();
                break;
        }

        return true;
    }

    public void onBackPressed() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(R.string.alert_exit_title);
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialogBuilder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Log.i(clsName, "BenchImage Particle finished");
                quit = true;
                finish();
            }
        });
        alertDialogBuilder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialogBuilder.setMessage(R.string.alert_exit_message);
        alertDialogBuilder.create().show();
    }

    private void processImage(AppConfiguration config, Filter filterLocal, CloudletFilter cloudletFilter,
                              InternetFilter internetFilter, long batteryBefore) {
        System.gc();

        if ((config.getFilter().equals("Cartoonizer") || config.getFilter().equals("Benchmark")) && vmSize <= 64 && (config.getSize().equals("8MP") || config.getSize().equals("4MP"))) {
            dialogSupportFilter();
        } else {
            if (config.getLocal().equals("Local")) {
                new ImageFilterTask(this, filterLocal, config, taskResultAdapter, batteryBefore).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else if (config.getLocal().equals("Cloudlet")) {
                new ImageFilterTask(this, cloudletFilter, config, taskResultAdapter, batteryBefore).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                new ImageFilterTask(this, internetFilter, config, taskResultAdapter, batteryBefore).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }

    }

    private void configureButton() {
        Button but = (Button) findViewById(R.id.button_execute);
        but.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.exec();
            }
        });
    }

    public void exec() {
        tempoTotalUI = 0;
        buttonStatusChange(R.id.button_execute, false, "Processando");
        setSpinnersState(false);
        Spinner spinnerQuantity = (Spinner) findViewById(R.id.spin_quantity);
        String aux = spinnerQuantity.getSelectedItem().toString();
        final int qtd = Integer.parseInt(aux);

        getConfigFromSpinner();
        configureStatusViewOnTaskStart();

        Thread t = new ThreadB(MainActivity.this, config, qtd, filterLocal, cloudletFilter, internetFilter);
        t.start();
    }

    class ThreadB extends Thread {
        private MainActivity mainActivity;
        private AppConfiguration config;
        private int qtd;
        private Filter filterLocal;
        private CloudletFilter cloudletFilter;
        private InternetFilter internetFilter;


        public ThreadB(MainActivity mainActivity, AppConfiguration config, int qtd,
                       Filter filterLocal, CloudletFilter cloudletFilter, InternetFilter internetFilter) {
            this.mainActivity = mainActivity;
            this.config = config;
            this.qtd = qtd;
            this.filterLocal = filterLocal;
            this.cloudletFilter = cloudletFilter;
            this.internetFilter = internetFilter;
        }

        @Override
        public void run() {
            int i = 0;

            while (true) {
                if (MainActivity.semaforo) {
                    if (i < qtd) {
                        MainActivity.semaforo = false;

                        final int INDICE = i + 1;
                        final int QUANTIDADE = qtd;

                        Log.i(clsName + " EXECUÇÃO", "Vai rodar pela " + INDICE + "º vez");
                        mainActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mainActivity, "Etapa: " + INDICE + "/" + QUANTIDADE, Toast.LENGTH_SHORT).show();
                            }
                        });
                        long batteryBefore = mainActivity.batteryLevel();

                        mainActivity.processImage(config, filterLocal, cloudletFilter, internetFilter, batteryBefore);
                        i++;
                    } else if (i == qtd) {
                        break;
                    }
                }
            }

            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.buttonStatusChange(R.id.button_execute, true, "Inicia");
                    mainActivity.setSpinnersState(true);
                    mainActivity.callbackEnding();
                }
            });
        }
    }

    private void configureSpinner() {
        Spinner spinnerImage = (Spinner) findViewById(R.id.spin_image);
        Spinner spinnerFilter = (Spinner) findViewById(R.id.spin_filter);
        Spinner spinnerSize = (Spinner) findViewById(R.id.spin_size);
        Spinner spinnerLocal = (Spinner) findViewById(R.id.spin_local);
        Spinner spinnerQuantity = (Spinner) findViewById(R.id.spin_quantity);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.spinner_img, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerImage.setAdapter(adapter);
        spinnerImage.setSelection(2);

        adapter = ArrayAdapter.createFromResource(this, R.array.spinner_filter, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);

        adapter = ArrayAdapter.createFromResource(this, R.array.spinner_local, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocal.setAdapter(adapter);

        adapter = ArrayAdapter.createFromResource(this, R.array.spinner_size, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSize.setAdapter(adapter);
        spinnerSize.setSelection(4);

        adapter = ArrayAdapter.createFromResource(this, R.array.spinner_quantity, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuantity.setAdapter(adapter);
        spinnerQuantity.setSelection(0);
    }

    private void configureStatusViewOnTaskStart() {
        configureStatusView("Status: Submetendo Tarefa");

        ImageView imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setImageBitmap(null);
    }

    private void configureStatusView(String status) {
        TextView tv_vmsize = (TextView) findViewById(R.id.text_vmsize);
        vmSize = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        tv_vmsize.setText("VMSize " + vmSize + "MB");

        if (vmSize < 128) {
            tv_vmsize.setTextColor(Color.RED);
        } else if (vmSize == 128) {
            tv_vmsize.setTextColor(Color.YELLOW);
        } else {
            tv_vmsize.setTextColor(Color.GREEN);
        }

        TextView tv_execucao = (TextView) findViewById(R.id.text_exec);
        tv_execucao.setText("Tempo de\nExecução: 0s");

        TextView tv_tamanho = (TextView) findViewById(R.id.text_size);
        tv_tamanho.setText("Tamanho/Foto: " + config.getSize() + "/" + photoName);

        TextView tv_status = (TextView) findViewById(R.id.text_status);
        tv_status.setText(status);
    }

    private void getConfigFromSpinner() {
        Spinner spinnerImage = (Spinner) findViewById(R.id.spin_image);
        Spinner spinnerFilter = (Spinner) findViewById(R.id.spin_filter);
        Spinner spinnerSize = (Spinner) findViewById(R.id.spin_size);
        Spinner spinnerLocal = (Spinner) findViewById(R.id.spin_local);

        photoName = (String) spinnerImage.getSelectedItem();
        config.setImage(photoNameToFileName(photoName));
        config.setLocal((String) spinnerLocal.getSelectedItem());

        config.setFilter((String) spinnerFilter.getSelectedItem());
        if (config.getFilter().equals("Benchmark")) {
            config.setSize("All");
        } else {
            config.setSize((String) spinnerSize.getSelectedItem());
        }
    }

    private void dialogSupportFilter() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Celular limitado!");
        alertDialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialogBuilder.setNegativeButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialogBuilder.setMessage("Celular não suporta o Cartoonizer, minimo recomendo é 128MB de VMSize");
        // cria e mostra
        alertDialogBuilder.create().show();

        buttonStatusChange(R.id.button_execute, true, "Inicia");
        setSpinnersState(true);
        TextView tv_status = (TextView) findViewById(R.id.text_status);
        tv_status.setText("Status: Requisição anterior não suporta Filtro!");
    }

    private void buttonStatusChange(int id, boolean state, String text) {
        Button but = (Button) findViewById(id);
        but.setEnabled(state);
        but.setText(text);
    }

    private void setSpinnersState(boolean flag) {
        spinnerStatusChange(R.id.spin_image, flag);
        spinnerStatusChange(R.id.spin_filter, flag);
        spinnerStatusChange(R.id.spin_size, flag);
        spinnerStatusChange(R.id.spin_local, flag);
        spinnerStatusChange(R.id.spin_quantity, flag);
    }

    private void spinnerStatusChange(int id, boolean state) {
        Spinner spinner = (Spinner) findViewById(id);
        spinner.setEnabled(state);
    }

    private String photoNameToFileName(String name) {
        if (name.equals("FAB Show")) {
            return "img1.jpg";
        } else if (name.equals("Cidade")) {
            return "img4.jpg";
        } else if (name.equals("SkyLine")) {
            return "img5.jpg";
        }
        return null;
    }

    public void createDirOutput() {
        Log.i(clsName, "createDirOutput()");
        String outputDir = getWorkSpacePath();

        File dir = new File(Environment.getExternalStorageDirectory(), outputDir);
        if (!dir.exists()) {
            boolean flag = dir.mkdirs();

            Log.i(clsName, "Criou pasta? " + flag);
        } else {
            Log.i(clsName, "Pasta já existe: " + outputDir);
        }

        config.setOutputDirectory(Environment.getExternalStorageDirectory() + File.separator + outputDir);
    }

    public String getWorkSpacePath() {
        Log.i(clsName, "getWorkSpacePath()");
//        File storage = Environment.getExternalStorageDirectory();
//        String outputDir = storage.getAbsolutePath() + File.separator + "BenchImageOutput";

        return "BenchImageOutput";
    }

    /**
     * Chamar esse método para realizar alguma coisa após todas as execuções
     *
     * @param params
     */
    public void callbackEnding(Object... params) {
        //batteryLevel();
        TextView tv_execucao = (TextView) findViewById(R.id.text_exec);
        double segundos = MainActivity.tempoTotalUI / 1000.0;
        tv_execucao.setText("Tempo de\nExecução: " + String.format("%.3f", segundos) + "s");
    }

    public long batteryLevel() {
        Intent intent = this.getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);

        boolean isPresent = intent.getBooleanExtra("present", false);

        Bundle bundle = intent.getExtras();
        String str = bundle.toString();
        Log.i(clsName + " EXECUÇÃO" + " Battery Info", str);

        if (isPresent) {
            int percent = (level * 100) / scale;

            Log.i(clsName + " EXECUÇÃO", "Technology = " + bundle.getString("technology"));
            Log.i(clsName + " EXECUÇÃO", "Voltage = " + bundle.getInt("voltage") + "mV");
            Log.i(clsName + " EXECUÇÃO", "Temperature = " + (bundle.getInt("temperature") / 10.0));
            Log.i(clsName + " EXECUÇÃO", "Current = " + bundle.getInt("current_avg"));
            Log.i(clsName + " EXECUÇÃO", "Percentage = " + percent + "%");

            return bundle.getInt("voltage");
        } else {
            Log.i(clsName + " EXECUÇÃO", "Battery not present!!!");

            return 0;
        }
    }

    private TaskResultAdapter<ResultImage> taskResultAdapter = new TaskResultAdapter<ResultImage>() {
        @Override
        public void completedTask(ResultImage obj) {
            if (obj != null) {
                final String LB = "\r\n";
                String log = "." + LB;
                ImageView imageView = (ImageView) findViewById(R.id.imageView);
                imageView.setImageBitmap(obj.getBitmap());

                TextView tv_tamanho = (TextView) findViewById(R.id.text_size);
                tv_tamanho.setText("Tamanho/Foto: " + config.getSize() + "/" + photoName);
                log += "Tamanho/Foto: " + config.getSize() + "/" + photoName + LB;

                TextView tv_execucao = (TextView) findViewById(R.id.text_exec);
                if (obj.getTotalTime() != 0) {
                    double segundos = obj.getTotalTime() / 1000.0;
                    tv_execucao.setText("Tempo de\nExecução: " + String.format("%.3f", segundos) + "s");
                    log += "Tempo de Execução: " + String.format("%.3f", segundos) + "s" + LB;
                } else {
                    tv_execucao.setText("Tempo de\nExecução: 0s");
                    log += "Tempo de Execução: 0s" + LB;
                }
                if (obj.getConfig().getFilter().equals("Benchmark")) {
                    double segundos = obj.getTotalTime() / 1000.0;
                    tv_execucao.setText("Tempo de\nExecução: " + String.format("%.3f", segundos) + "s");
                    log += "Tempo de Execução: " + String.format("%.3f", segundos) + "s" + LB;
                }

                Log.i(clsName + " EXECUÇÃO", log);
                MainActivity.tempoTotalUI += obj.getTotalTime();
            } else {
                TextView tv_status = (TextView) findViewById(R.id.text_status);
                tv_status.setText("Status: Algum Error na transmissão!");
            }
            Log.i(clsName + " EXECUÇÃO", "-- Finalizando execução -- ");
            MainActivity.semaforo = true;
        }

        @Override
        public void taskOnGoing(int completed, String statusText) {
            TextView tv_status = (TextView) findViewById(R.id.text_status);
            tv_status.setText("Status: " + statusText);
        }
    };
}