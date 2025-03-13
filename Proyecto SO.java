import java.io.*;
import java.util.concurrent.*;

public class ProductorConsumidor {
    private static final int BUFFER_SIZE = 10;
    private static final BlockingQueue<Integer> buffer = new ArrayBlockingQueue<>(BUFFER_SIZE);
    
    public static void main(String[] args) {
        Thread productor = new Thread(new Productor("numeros.txt"));
        Thread consumidorPar = new Thread(new Consumidor("Pares", n -> n % 2 == 0));
        Thread consumidorImpar = new Thread(new Consumidor("Impares", n -> n % 2 != 0));
        Thread consumidorPrimo = new Thread(new Consumidor("Primos", ProductorConsumidor::esPrimo));
        
        productor.start();
        consumidorPar.start();
        consumidorImpar.start();
        consumidorPrimo.start();
    }
    
    static boolean esPrimo(int num) {
        if (num < 2) return false;
        for (int i = 2; i <= Math.sqrt(num); i++) {
            if (num % i == 0) return false;
        }
        return true;
    }
    
    static class Productor implements Runnable {
        private final String archivo;
        
        public Productor(String archivo) {
            this.archivo = archivo;
        }
        
        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    int numero = Integer.parseInt(linea.trim());
                    buffer.put(numero);
                    System.out.println("Productor: Insertó " + numero);
                }
                buffer.put(-1); // Señal de finalización
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    static class Consumidor implements Runnable {
        private final String nombre;
        private final java.util.function.Predicate<Integer> filtro;
        
        public Consumidor(String nombre, java.util.function.Predicate<Integer> filtro) {
            this.nombre = nombre;
            this.filtro = filtro;
        }
        
        @Override
        public void run() {
            int suma = 0;
            try {
                while (true) {
                    int numero = buffer.take();
                    if (numero == -1) {
                        buffer.put(-1); // Pasar la señal a otros consumidores
                        break;
                    }
                    if (filtro.test(numero)) {
                        suma += numero;
                        System.out.println(nombre + " consumió: " + numero + " | Suma: " + suma);
                    } else {
                        buffer.put(numero); // Reinsertar si no es su tipo
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
