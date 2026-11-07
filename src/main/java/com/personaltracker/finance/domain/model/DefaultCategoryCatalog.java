package com.personaltracker.finance.domain.model;

import java.util.List;

/**
 * Catálogo colombiano de categorías sugeridas (ADR-0014). El usuario lo acepta en la bienvenida de
 * Finanzas y después lo edita libremente. El 4x1000 se registra a mano en su subcategoría.
 */
public final class DefaultCategoryCatalog {

    /** Categoría principal sugerida con sus subcategorías. */
    public record Entry(CategoryKind kind, String name, List<String> subcategories) {

        public Entry {
            subcategories = List.copyOf(subcategories);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            expense("Vivienda", "Arriendo", "Administración", "Servicios públicos", "Internet y telefonía",
                    "Mantenimiento y reparaciones"),
            expense("Alimentación", "Mercado", "Restaurantes", "Domicilios"),
            expense("Transporte", "Combustible", "Transporte público", "Taxis y apps", "Parqueaderos y peajes"),
            expense("Salud", "Medicina prepagada y seguros", "Medicamentos", "Citas y exámenes"),
            expense("Educación", "Matrículas y pensiones", "Cursos", "Libros y materiales"),
            expense("Entretenimiento", "Suscripciones", "Salidas", "Viajes"),
            expense("Compras", "Ropa y calzado", "Tecnología", "Hogar", "Cuidado personal"),
            expense("Servicios financieros", "4x1000", "Cuota de manejo", "Intereses", "Comisiones"),
            expense("Impuestos", "Predial", "Vehículo", "Renta"),
            expense("Otros gastos"),
            income("Salario"),
            income("Honorarios"),
            income("Ventas"),
            income("Rendimientos"),
            income("Otros ingresos"));

    private DefaultCategoryCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static Entry expense(String name, String... subcategories) {
        return new Entry(CategoryKind.EXPENSE, name, List.of(subcategories));
    }

    private static Entry income(String name, String... subcategories) {
        return new Entry(CategoryKind.INCOME, name, List.of(subcategories));
    }
}
