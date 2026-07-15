package pe.torreblanca.backend.util;

import java.util.regex.Pattern;

/**
 * Reglas de validación centralizadas para evitar caracteres especiales
 * en los campos de texto del sistema (requerimiento del docente).
 * El campo de contraseña queda exento a propósito: los caracteres
 * especiales ahí mejoran la seguridad, no la comprometen.
 */
public class ValidacionUtil {

    // Letras (con tildes y ñ), espacios y guión — para nombres y apellidos
    private static final Pattern PATRON_LETRAS = Pattern.compile("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s-]+$");

    // Exactamente 8 dígitos — DNI peruano
    private static final Pattern PATRON_DNI = Pattern.compile("^\\d{8}$");

    // Código de país (+51, etc.) seguido de 6 a 12 dígitos — teléfonos con prefijo internacional
    private static final Pattern PATRON_TELEFONO = Pattern.compile("^\\+\\d{1,4}\\d{6,12}$");

    // Formato de email estándar
    private static final Pattern PATRON_EMAIL = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");

    // Letras, números y espacios — para descripciones y observaciones (estricto)
    private static final Pattern PATRON_TEXTO_LIBRE = Pattern.compile("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü0-9\\s]+$");

    public static void validarNombre(String valor, String nombreCampo) {
        if (valor == null || valor.trim().isEmpty())
            throw new RuntimeException(nombreCampo + " es obligatorio");
        if (!PATRON_LETRAS.matcher(valor.trim()).matches())
            throw new RuntimeException(nombreCampo + " solo puede contener letras y espacios, sin caracteres especiales");
    }

    public static void validarDni(String valor) {
        if (valor == null || valor.trim().isEmpty()) return; // campo opcional
        if (!PATRON_DNI.matcher(valor.trim()).matches())
            throw new RuntimeException("El DNI debe tener exactamente 8 dígitos numéricos");
    }

    public static void validarTelefono(String valor) {
        if (valor == null || valor.trim().isEmpty()) return; // campo opcional
        if (!PATRON_TELEFONO.matcher(valor.trim()).matches())
            throw new RuntimeException("El teléfono debe incluir el código de país (ej. +51) seguido del número");
    }

    public static void validarEmail(String valor) {
        if (valor == null || valor.trim().isEmpty())
            throw new RuntimeException("El email es obligatorio");
        if (!PATRON_EMAIL.matcher(valor.trim()).matches())
            throw new RuntimeException("El formato del email no es válido");
    }

    // Para descripción de gastos, observaciones de pagos, número de operación, etc.
    public static void validarTextoLibre(String valor, String nombreCampo) {
        if (valor == null || valor.trim().isEmpty()) return; // campo opcional, validar requerido aparte si aplica
        if (!PATRON_TEXTO_LIBRE.matcher(valor.trim()).matches())
            throw new RuntimeException(nombreCampo + " solo puede contener letras, números y espacios, sin caracteres especiales");
    }

    public static void validarTextoLibreRequerido(String valor, String nombreCampo) {
        if (valor == null || valor.trim().isEmpty())
            throw new RuntimeException(nombreCampo + " es obligatorio");
        validarTextoLibre(valor, nombreCampo);
    }
}
