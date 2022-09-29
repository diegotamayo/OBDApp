package com.example.obdapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.amplifyframework.AmplifyException;
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin;
import com.amplifyframework.core.Amplify;
import com.amplifyframework.storage.s3.AWSS3StoragePlugin;
import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.LoadCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;


public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothSocket btSocket;
    private String chosenDeviceName, chosenDeviceAddress;

    //region Declaraciones Buttons
    private Button bConnect;
    private Button bStart;
    private Button bStop;
    private Button bChooseDevice;
    private Button bExitApp;
    private Button bDownload;
    //endregion

    //region Declaraciones Results
    // Speed Results (1)
    private TextView command_SpeedResult;
    // Engine Results (3)
    private TextView command_LoadResult, command_RPMResult;
    // Temperature Results (1)
    private TextView command_EngineCoolantTemperatureResult;
    // Location (2)
    private TextView command_LatitudeResult, command_LongitudeResult;
    // Sensor Results (1)
    private TextView command_AccelerationXResult, command_AccelerationYResult, command_AccelerationZResult;
    //endregion

    private TextView info;

    //region Instanciación Commands
    // Speed (1)
    private ObdCommand command_Speed = new SpeedCommand();
    // Engine (3)
    private ObdCommand command_Load = new LoadCommand();
    private ObdCommand command_RPM = new RPMCommand();
    // Temperature (1)
    private ObdCommand command_EngineCoolantTemperature = new EngineCoolantTemperatureCommand();
    //endregion

    //region variables SensorManager y Timer
    private SensorManager sensorManager;
    private Timer myTimer;
    //endregion

    private static final int REQUEST_LOCATION = 1;
    private FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationManager locationManager;

    //region variables de Ubicación
    private String latitud_value;
    private String longitud_value;
    //endregion


    // Método principal de la actividad.
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //region Asociaciones Command Results
        // Speed (1)
        command_SpeedResult = findViewById(R.id.command_SpeedResult);
        // Engine (3)
        command_LoadResult = findViewById(R.id.command_EngineLoadResult);
        command_RPMResult = findViewById(R.id.command_RPMResult);
        // Temperature (1)
        command_EngineCoolantTemperatureResult = findViewById(R.id.command_EngineCoolantTemperatureResult);
        // Location (2)
        command_LatitudeResult = findViewById(R.id.command_LatitudeResult);
        command_LongitudeResult = findViewById(R.id.command_LongitudeResult);
        // Sensor (1)
        command_AccelerationXResult = findViewById(R.id.command_AccelerationXResult);
        command_AccelerationYResult = findViewById(R.id.command_AccelerationYResult);
        command_AccelerationZResult = findViewById(R.id.command_AccelerationZResult);
        //endregion

        //region Asociaciones Buttons y Info
        bChooseDevice = findViewById(R.id.bChooseDevice);
        bConnect = findViewById(R.id.bConnect);
        bStart = findViewById(R.id.bStart);
        bStop = findViewById(R.id.bStop);
        bExitApp = findViewById(R.id.bExitApp);
        bDownload = findViewById(R.id.bDownload);
        info = findViewById(R.id.info);
        //endregion

        //region Buttons Click Events
        bChooseDevice.setOnClickListener(e -> chooseBluetoothDevice());
        bConnect.setOnClickListener(e -> connectOBD());

        bStart.setOnClickListener(e -> {
            // Se inician plugins de AWS.
            try {
                Amplify.addPlugin(new AWSCognitoAuthPlugin());
                Amplify.addPlugin(new AWSS3StoragePlugin());
                Amplify.configure(getApplicationContext());
                // Se muestran mensajes de notificación de inicio de plugins AWS.
                Log.i("MyAmplifyApp", "Initialized Amplify");
                Toast.makeText(this, "Servicio AWS inicializado...", Toast.LENGTH_SHORT).show();
                // Se inicia lectura de datos.
                startOBD();
            } catch (AmplifyException amplifyException) {
                Log.e("MyAmplifyApp", "Could not initialize Amplify", amplifyException);
                Toast.makeText(getApplicationContext(), "AmplifyException: Could not initialize Amplify... " + amplifyException.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (RuntimeException runtimeException) {
                Toast.makeText(getApplicationContext(), "RuntimeException: Could not initialize Amplify... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        bStop.setOnClickListener(e -> {
            try {
                stopOBD(0);
            } catch (RuntimeException runtimeException) {
                Toast.makeText(getApplicationContext(), "RuntimeException: Could not initialize Amplify... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        bExitApp.setOnClickListener(e -> {
            try {
                stopOBD(0);
            } catch (RuntimeException runtimeException) {
                Toast.makeText(getApplicationContext(), "RuntimeException: Could not initialize Amplify... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        bDownload.setOnClickListener(e -> downloadObject());
        //endregion

        //region Button Properties
        bConnect.setEnabled(false);
        bStart.setEnabled(false);
        bStop.setEnabled(false);
        bExitApp.setEnabled(false);
        bDownload.setEnabled(false);
        //endregion

        //region Accelerometer manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        //endregion

        //region Location manager
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        //endregion

        //region Location request
        locationRequest = locationRequest.create();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(400);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //endregion

        //region Location callback
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    if (locationResult == null) {
                        return;
                    }
                    //Showing the latitude, longitude and accuracy.
                    for (Location location : locationResult.getLocations()) {
                        latitud_value = String.valueOf(location.getLatitude());
                        longitud_value = String.valueOf(location.getLongitude());
                    }
                } else {
                    latitud_value = String.valueOf(0);
                    longitud_value = String.valueOf(0);
                }
            }
        };
        //endregion

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(listener);
        }
    }

    private float currentAccelerationXValue = 0;
    private float currentAccelerationYValue = 0;
    private float currentAccelerationZValue = 0;


    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            currentAccelerationXValue = event.values[0];
            currentAccelerationYValue = event.values[1];
            currentAccelerationZValue = event.values[2];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    // Método para escribir la cabecera del archivo csv.
    private void writeCsvHeader(FileWriter fileWriter) {
        try {
            String line = String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s\n", "Fecha", "Velocity", "Eng_Load", "RPM", "Eng_Cool_Temp", "Accel_X", "Accel_Y", "Accel_Z", "Latitude", "Longitude");
            fileWriter.write(line);
        } catch (IOException ioException) {
            Toast.makeText(getApplicationContext(), "IOException: Could not write Csv Header... " + ioException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // Método para escribir las unidades de la fila de cabecera del archivo csv.
    private void writeCsvHeaderUnits(FileWriter fileWriter) {
        try {
            String strFecha = "yyyyMMdd_hhmmss";
            // Lectura de las unidades para 'Velocidad'.
            command_Speed.run(btSocket.getInputStream(), btSocket.getOutputStream());
            String strSpeedUnit = "m/s";
            // Lectura de las unidades para 'Carga del motor'.
            command_Load.run(btSocket.getInputStream(), btSocket.getOutputStream());
            String strLoadUnit = command_Load.getResultUnit();
            // Lectura de las unidades para 'RPM'.
            command_RPM.run(btSocket.getInputStream(), btSocket.getOutputStream());
            String strRPMUnit = command_RPM.getResultUnit();
            // Lectura de las unidades para 'Temperatura del refrigerante'.
            command_EngineCoolantTemperature.run(btSocket.getInputStream(), btSocket.getOutputStream());
            String strEngineCoolantTemperature = command_EngineCoolantTemperature.getResultUnit();
            // Lectura de las unidades para 'Velocidad/RPM'.
            String strAccelerationX = "m/s²";
            String strAccelerationY = "m/s²";
            String strAccelerationZ = "m/s²";
            String strLatitude = "decimal_degrees";
            String strLongitude = "decimal_degrees";
            String line = String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s\n", strFecha, strSpeedUnit, strLoadUnit, strRPMUnit, strEngineCoolantTemperature, strAccelerationX, strAccelerationY, strAccelerationZ, strLatitude, strLongitude);
            fileWriter.write(line);
        } catch (IOException ioException) {
            Toast.makeText(getApplicationContext(), "IOException: Could not write Csv Header... " + ioException.getMessage(), Toast.LENGTH_LONG).show();
        } catch (InterruptedException interruptedException) {
            Toast.makeText(getApplicationContext(), "InterruptedException: Could not write Csv Header... " + interruptedException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // Método para escribir filas en el archivo csv.
    private void writeCsvData(String fecha, float velocity, float engine_load, float rpm, float engine_coolant_temperature, float accelerationX, float accelerationY, float accelerationZ, float latitude, float longitude, FileWriter fileWriter) {
        try {
            @SuppressLint("DefaultLocale") String line = String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.6f,%.6f\n", fecha, velocity, engine_load, rpm, engine_coolant_temperature, accelerationX, accelerationY, accelerationZ, latitude, longitude);
            fileWriter.write(line);
        } catch (IOException ioException) {
            Toast.makeText(getApplicationContext(), "IOException: Could not write Csv Data... " + ioException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // Método para registrar resultados de actividades.
    ActivityResultLauncher<Intent> getResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent intent = result.getData();
                    assert intent != null;
                    String data = intent.getStringExtra("requestCode");
                    if (data.equals("ENABLE_BT_REQUEST")) {
                        continueBluetooth();
                    }
                }
                if (result.getResultCode() == RESULT_CANCELED) {
                    Intent intent = result.getData();
                    assert intent != null;
                    String data = intent.getStringExtra("requestCode");
                    if (data.equals("ENABLE_BT_REQUEST")) {
                        Toast.makeText(MainActivity.this, "M1 Application requires Bluetooth enabled", Toast.LENGTH_LONG).show();
                    }
                }
            });


    // Método para seleccionar dispositivo Bluetooth.
    private void chooseBluetoothDevice() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "M3 Device doesn't support Bluetooth", Toast.LENGTH_LONG).show();
        }
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.putExtra("requestCode", "ENABLE_BT_REQUEST");
            getResult.launch(enableBtIntent);
        } else {
            continueBluetooth();
        }
    }


    // Método complementario en la selección del dispositivo Bluetooth.
    private void continueBluetooth() {
        final ArrayList<String> pairedDevicesNames = new ArrayList<>();
        final ArrayList<String> pairedDevicesAddresses = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    pairedDevicesNames.add(device.getName());
                    pairedDevicesAddresses.add(device.getAddress());
                }

                final String[] devicesString = pairedDevicesNames.toArray(new String[0]);
                AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
                mBuilder.setTitle("Seleccionar dispositivo OBD:");
                mBuilder.setSingleChoiceItems(devicesString, -1, (dialog, i) -> {
                    dialog.dismiss();
                    int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    chosenDeviceAddress = pairedDevicesAddresses.get(position);
                    chosenDeviceName = pairedDevicesNames.get(position);
                    Toast.makeText(MainActivity.this, "Dispositivo elegido: " + chosenDeviceName, Toast.LENGTH_SHORT).show();
                    info.setText(String.format("Dispositivo: %s\tDirección: %s", chosenDeviceName, chosenDeviceAddress));
                    bConnect.setEnabled(true);
                });

                AlertDialog mDialog = mBuilder.create();
                mDialog.show();
            } else {
                Toast.makeText(getApplicationContext(), "No se encontraron dispositivos emparejados...", Toast.LENGTH_SHORT).show();
            }
        }

    }


    // Método para conectar el dispositivo Bluetooth seleccionado.
    private void connectOBD() {
        try {
            BluetoothDevice device = btAdapter.getRemoteDevice(chosenDeviceAddress);
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                btSocket = device.createRfcommSocketToServiceRecord(uuid);
                btSocket.connect();

                new EchoOffCommand().run(btSocket.getInputStream(), btSocket.getOutputStream());
                new LineFeedOffCommand().run(btSocket.getInputStream(), btSocket.getOutputStream());
                new SelectProtocolCommand(ObdProtocols.AUTO).run(btSocket.getInputStream(), btSocket.getOutputStream());

                Toast.makeText(MainActivity.this, "Conectado al dispositivo OBD...", Toast.LENGTH_SHORT).show();
                bStart.setEnabled(true);
                bConnect.setEnabled(false);
            }
        } catch (IllegalArgumentException e) {
            Toast.makeText(MainActivity.this, "Seleccione primero el dispositvo Bluetooth... ", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "No se puede establecer conexión... ", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "An error ocurred in OBD connection... " + e, Toast.LENGTH_LONG).show();
        }
    }


    // Se definen variables para File y FileWriter.
    private File file;
    private FileWriter fileWriter;

    // Declaración de otras variables.
    private long tStart;
    private long tEnd;
    private long tDelta;
    private double elapsedSeconds;
    private int counter;
    private String fecha;
    private float velocidad_kph;
    private float velocidad;
    private float engineLoad;
    private float rpm;
    private float engineCoolantTemperature;
    private float latitud;
    private float longitud;
    private String nombreArchivo;


    // Método que inicia la lectura desde la interfaz OBD-II.
    private void startOBD() {
        // Se limpia el contenido de los controles TextView.
        command_SpeedResult.setText("");
        command_LoadResult.setText("");
        command_RPMResult.setText("");
        command_EngineCoolantTemperatureResult.setText("");

        // Se inicializa el estado de los botones.
        bStart.setEnabled(false);
        bConnect.setEnabled(false);
        bChooseDevice.setEnabled(false);
        bStop.setEnabled(true);
        bExitApp.setEnabled(true);
        //bDownload.setEnabled(true);

        // Se inicializan variables.
        long frecuencia = 2000;
        myTimer = new Timer();
        tStart = System.currentTimeMillis();

        // Se crea el archivo inicial.
        crearArchivo(true);

        myTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            // Se ejecuta el proceso cada 2 segundos.
            public void run() {
                // Se obtiene la fecha actual.
                tEnd = System.currentTimeMillis();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_hhmmss");
                Date date = new Date(tEnd);
                fecha = simpleDateFormat.format(date);
                // Se calcula la variación de tiempo.
                tDelta = tEnd - tStart;
                elapsedSeconds = tDelta / 1000.0;

                counter = counter + 1;

                if (counter >= 1) {
                    // Se realiza la lectura de valores desde el vehículo.
                    try {
                        // Lectura de datos de 'Velocidad'.
                        command_Speed.run(btSocket.getInputStream(), btSocket.getOutputStream());
                        command_SpeedResult.setText(command_Speed.getCalculatedResult());
                        velocidad_kph = Float.parseFloat(command_SpeedResult.getText().toString());
                        velocidad = velocidad_kph / 3.6f;
                        // Lectura de datos de 'Carga del motor'.
                        command_Load.run(btSocket.getInputStream(), btSocket.getOutputStream());
                        command_LoadResult.setText(command_Load.getCalculatedResult());
                        engineLoad = Float.parseFloat(command_LoadResult.getText().toString());
                        // Lectura de datos de 'RPM'.
                        command_RPM.run(btSocket.getInputStream(), btSocket.getOutputStream());
                        command_RPMResult.setText(command_RPM.getCalculatedResult());
                        rpm = Float.parseFloat(command_RPMResult.getText().toString());
                        // Lectura de datos de 'Temperatura del refrigerante'.
                        command_EngineCoolantTemperature.run(btSocket.getInputStream(), btSocket.getOutputStream());
                        command_EngineCoolantTemperatureResult.setText(command_EngineCoolantTemperature.getCalculatedResult());
                        engineCoolantTemperature = Float.parseFloat(command_EngineCoolantTemperatureResult.getText().toString());
                        // Cálculo de latitud y longitud.
                        latitud = Float.parseFloat(latitud_value);
                        longitud = Float.parseFloat(longitud_value);
                        command_LatitudeResult.setText(latitud_value);
                        command_LongitudeResult.setText(longitud_value);
                        // Cálculo de la aceleración.
                        command_AccelerationXResult.setText(Float.toString(currentAccelerationXValue));
                        command_AccelerationYResult.setText(Float.toString(currentAccelerationYValue));
                        command_AccelerationZResult.setText(Float.toString(currentAccelerationZValue));
                    } catch (RuntimeException runtimeException) {
                        Toast.makeText(getApplicationContext(), "RuntimeException: Could not initialize Timer... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
                    } catch (IOException ioException) {
                        Toast.makeText(getApplicationContext(), "IOException: Could not initialize Timer... " + ioException.getMessage(), Toast.LENGTH_LONG).show();
                    } catch (InterruptedException interruptedException) {
                        Toast.makeText(getApplicationContext(), "InterruptedException: Could not initialize Timer... " + interruptedException.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    // Se muestra el tiempo transcurrido en segundos.
                    info.setText(String.format("Tiempo (s): %s. Contador: %s", elapsedSeconds, counter));
                    // Se escriben los datos de la lectura en una nueva fila del archivo.
                    writeCsvData(fecha, velocidad, engineLoad, rpm, engineCoolantTemperature, currentAccelerationXValue, currentAccelerationYValue, currentAccelerationZValue, latitud, longitud, fileWriter);

                    // Se verifica si el contador es múltiplo de 150.
                    if (counter % 150 == 0) { // 150: 5 minutos
                        uploadFile(nombreArchivo, file, counter);
                        // Se cierra el archivo actual.
                        cerrarArchivo(fileWriter);
                        // Se genera una copia del archivo en el almacenamiento local.
                        String partesNombreArchivoFuente[] = nombreArchivo.split("\\.", 2);
                        String nombreArchivoDestino = partesNombreArchivoFuente[0] + "_local.csv";
                        File archivoDestino = new File(getFilesDir(), nombreArchivoDestino);

                        try {
                            guardarArchivoLocal(file, archivoDestino);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // Se crea un nuevo archivo.
                        crearArchivo(false);
                    }
                    // Se verifica si se cumple el límite máximo de tiempo en segundos.
                    if (counter == 36000){ // 1800: 1 hora, 36.000: 20 horas
                        info.setText(String.format("El proceso ha finalizado con éxito. Tiempo empleado (s): %s", elapsedSeconds));
                    }
                    if (counter == 36005){
                        stopOBD(counter);
                    }
                }

            }
        }, 0, frecuencia);
    }


    // Método que detiene la lectura desde la interfaz OBD-II.
    private void stopOBD(int counter) {
        // Se finaliza el objeto 'Timer'.
        myTimer.cancel();
        // Se limpian los campos de las lecturas.
        command_SpeedResult.setText("");
        command_LoadResult.setText("");
        command_RPMResult.setText("");
        command_EngineCoolantTemperatureResult.setText("");
        command_AccelerationXResult.setText("");
        command_AccelerationYResult.setText("");
        command_AccelerationZResult.setText("");
        command_LatitudeResult.setText("");
        command_LongitudeResult.setText("");
        // Se muestra un mensaje de confirmación del archivo guardado.
        Toast.makeText(getApplicationContext(), "Archivo " + file + " guardado con éxito.", Toast.LENGTH_LONG).show();
        file.delete();
        try {
            bStart.setEnabled(false);
            bStop.setEnabled(false);
            bConnect.setEnabled(false);
            bChooseDevice.setEnabled(false);
            // Se sube el archivo generado a AWS.
            // Se liberan los recursos del archivo generado.
            assert fileWriter != null;
            fileWriter.flush();
            fileWriter.close();
            // Se finaliza la aplicación.
            MainActivity.this.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (IOException ioException) {
            Toast.makeText(getApplicationContext(), "IOException: Could not stop data reading... " + ioException.getMessage(), Toast.LENGTH_LONG).show();
        } catch (RuntimeException runtimeException) {
            Toast.makeText(getApplicationContext(), "RuntimeException: Could not stop data reading... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // Método para subir archivo a repositorio en AWS.
    private void uploadFile(String nombreArchivo, File file, int counter) {
        try {
            Amplify.Storage.uploadFile(
                    nombreArchivo,
                    file,
                    result -> Toast.makeText(getApplicationContext(), "Archivo " + file + "(" + counter + ")" + " subido a Amazon S3 con éxito", Toast.LENGTH_LONG).show(),
                    storageFailure -> Log.i("MyAmplifyApp", "Upload failed", storageFailure)
            );
        } catch (RuntimeException runtimeException) {
            Toast.makeText(getApplicationContext(), "RuntimeException: Could not upload file to AWS... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // Método para guardar archivo en el teléfono.
    private void guardarArchivoLocal(File archivoFuente, File archivoDestino) throws IOException {
        try {
            FileChannel canalIngreso = null;
            FileChannel canalSalida = null;
            canalIngreso = new FileInputStream(file).getChannel();
            canalSalida = new FileOutputStream(archivoDestino).getChannel();
            canalIngreso.transferTo(0, canalIngreso.size(), canalSalida);
            if (canalIngreso != null)
                canalIngreso.close();
            if (canalSalida != null)
                canalSalida.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Método para crear nuevo archivo csv.
    private void crearArchivo(boolean blnArchivoInicial) {
        // Se construye el nombre del archivo con el formato "yyyyMMdd_HHmmss".
        @SuppressLint("SimpleDateFormat") SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        nombreArchivo = fmt.format(date) + ".csv";
        if (blnArchivoInicial == false) {
            // Se muestra el nombre del archivo.
            info.setText(String.format("Archivo: %s", nombreArchivo));
        }
        // Se crea archivo para almacenar los datos leídos.
        file = new File(getFilesDir(), nombreArchivo);
        try {
            fileWriter = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Se escriben las filas de la cabecera del archivo.
        writeCsvHeader(fileWriter);
        writeCsvHeaderUnits(fileWriter);
    }


    // Método para cerrar archivo.
    private void cerrarArchivo(FileWriter fileWriter) {
        assert fileWriter != null;
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Método para descargar objeto de AWS.
    private void downloadObject() {
        // Se inician plugins de AWS.
        try {
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.addPlugin(new AWSS3StoragePlugin());
            Amplify.configure(getApplicationContext());
            // Se muestran mensajes de notificación de inicio de plugins AWS.
            Log.i("MyAmplifyApp", "Initialized Amplify");
            Toast.makeText(this, "Servicio AWS inicializado...", Toast.LENGTH_SHORT).show();
            // Se inicia lectura de datos.
            String strNombreArchivo = "20220619_181109.csv";
            Amplify.Storage.downloadFile(
                    strNombreArchivo,
                    new File(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/downloaded_file.csv"),
                    result -> Toast.makeText(getApplicationContext(), "Archivo " + result.getFile().getAbsolutePath() + " descargado desde S3 con éxito", Toast.LENGTH_LONG).show(),
                    error -> Log.e("MyAmplifyApp",  "Download Failure", error)
            );
        } catch (AmplifyException amplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", amplifyException);
            Toast.makeText(getApplicationContext(), "AmplifyException: Could not initialize Amplify... " + amplifyException.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (RuntimeException runtimeException) {
            Toast.makeText(getApplicationContext(), "RuntimeException: Could not initialize Amplify... " + runtimeException.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}