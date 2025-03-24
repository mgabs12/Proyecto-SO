import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductorConsumidor {
    public static void main(String[] args) {
        final int BUFFER_SIZE = 10;
        Buffer buffer = new Buffer(BUFFER_SIZE);
        AtomicBoolean running = new AtomicBoolean(true);
        
        // Crear archivo de números de prueba si no existe
        String filename = "numeros.txt";
        File file = new File(filename);
        
        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Generar algunos números de prueba (del 1 al 50)
                for (int i = 1; i <= 50; i++) {
                    writer.println(i);
                }
                System.out.println("Archivo de prueba creado: " + filename);
            } catch (IOException e) {
                System.err.println("Error al crear el archivo de prueba: " + e.getMessage());
                return;
            }
        }
        
        // Crear y ejecutar el hilo productor
        Thread productorThread = new Thread(new Productor(buffer, filename));
        productorThread.start();
        
        // Crear y ejecutar los hilos consumidores
        List<Thread> consumidorThreads = new ArrayList<>();
        
        // Crear 3 consumidores (pares, impares, primos)
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(new Consumidor(buffer, i, running));
            consumidorThreads.add(t);
            t.start();
        }
        
        // Esperar a que el productor termine
        try {
            productorThread.join();
        } catch (InterruptedException e) {
            System.err.println("Error al esperar al productor: " + e.getMessage());
        }
        
        // Esperar a que los consumidores terminen
        running.set(false);
        for (Thread t : consumidorThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Error al esperar a un consumidor: " + e.getMessage());
            }
        }
        
        System.out.println("Programa finalizado");
    }
}

// Clase Buffer para almacenar los datos compartidos
class Buffer {
    private int[] data;
    private int capacity;
    private int count = 0;
    private int writePos = 0;
    private int readPos = 0;
    
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    
    private boolean finished = false;
    
    public Buffer(int size) {
        this.capacity = size;
        this.data = new int[size];
    }
    
    // Método para que el productor añada un elemento
    public void produce(int item) {
        lock.lock();
        try {
            // Esperar si el buffer está lleno
            while (count == capacity) {
                notFull.await();
            }
            
            // Añadir el elemento al buffer
            data[writePos] = item;
            writePos = (writePos + 1) % capacity;
            count++;
            
            // Notificar a los consumidores que hay un nuevo elemento
            notEmpty.signalAll();
        } catch (InterruptedException e) {
            System.err.println("Productor interrumpido: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    // Método para que los consumidores tomen un elemento
    public int consume() {
        lock.lock();
        try {
            // Esperar si el buffer está vacío o si se ha terminado de producir
            while (count == 0) {
                if (finished) {
                    return -1; // Señal de finalización
                }
                notEmpty.await();
            }
            
            // Tomar un elemento del buffer
            int item = data[readPos];
            readPos = (readPos + 1) % capacity;
            count--;
            
            // Notificar al productor que hay espacio disponible
            notFull.signal();
            
            return item;
        } catch (InterruptedException e) {
            System.err.println("Consumidor interrumpido: " + e.getMessage());
            return -1;
        } finally {
            lock.unlock();
        }
    }
    
    // Método para señalar que el productor ha terminado
    public void setFinished() {
        lock.lock();
        try {
            finished = true;
            notEmpty.signalAll(); // Despertar a todos los consumidores
        } finally {
            lock.unlock();
        }
    }
    
    // Método para verificar si un número es primo
    public static boolean esPrimo(int num) {
        if (num <= 1) return false;
        if (num <= 3) return true;
        if (num % 2 == 0 || num % 3 == 0) return false;
        
        for (int i = 5; i * i <= num; i += 6) {
            if (num % i == 0 || num % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }
}

// Clase Productor
class Productor implements Runnable {
    private Buffer buffer;
    private String filename;
    
    public Productor(Buffer buffer, String filename) {
        this.buffer = buffer;
        this.filename = filename;
    }
    
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    int num = Integer.parseInt(line.trim());
                    buffer.produce(num);
                    
                    // Simulación de tiempo de producción
                    Thread.sleep(100);
                    
                    System.out.println("Productor: Agregó " + num + " al buffer");
                } catch (NumberFormatException e) {
                    System.err.println("Error al parsear número: " + e.getMessage());
                } catch (InterruptedException e) {
                    System.err.println("Productor interrumpido: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al leer el archivo " + filename + ": " + e.getMessage());
        } finally {
            buffer.setFinished();
            System.out.println("Productor: Ha terminado de producir");
        }
    }
}

// Clase Consumidor
class Consumidor implements Runnable {
    private Buffer buffer;
    private int id;
    private AtomicBoolean running;
    
    public Consumidor(Buffer buffer, int id, AtomicBoolean running) {
        this.buffer = buffer;
        this.id = id;
        this.running = running;
    }
    
    @Override
    public void run() {
        int suma = 0;
        int count = 0;
        
        while (running.get()) {
            int item = buffer.consume();
            
            // Si se recibe la señal de finalización
            if (item == -1) {
                break;
            }
            
            boolean consumir = false;
            
            // Decidir si este consumidor debe procesar el número
            switch (id) {
                case 0: // Consumidor de números pares
                    consumir = (item % 2 == 0);
                    break;
                case 1: // Consumidor de números impares
                    consumir = (item % 2 != 0);
                    break;
                case 2: // Consumidor de números primos
                    consumir = Buffer.esPrimo(item);
                    break;
            }
            
            if (consumir) {
                suma += item;
                count++;
                
                // Determinar el tipo de consumidor
                String tipo;
                if (id == 0) tipo = "Pares";
                else if (id == 1) tipo = "Impares";
                else tipo = "Primos";
                
                System.out.println("Consumidor " + id + " (" + tipo + "): Consumió " + item 
                        + ", Suma actual: " + suma);
                
                // Simulación de tiempo de consumo
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    System.err.println("Consumidor interrumpido: " + e.getMessage());
                    break;
                }
            } else {
                // Devolver al buffer los elementos que no corresponden a este consumidor
                buffer.produce(item);
            }
        }
        
        // Determinar el tipo de consumidor para el mensaje final
        String tipo;
        if (id == 0) tipo = "Pares";
        else if (id == 1) tipo = "Impares";
        else tipo = "Primos";
        
        System.out.println("Consumidor " + id + " (" + tipo + ") ha terminado. "
                + "Suma total: " + suma + ", Elementos procesados: " + count);
    }
}