import random


class ChileanRutGenerator:
    """Genera RUTs chilenos válidos usando algoritmo Módulo 11."""

    FACTORS = (2, 3, 4, 5, 6, 7)

    @classmethod
    def from_number(cls, base_number: int) -> str:
        reversed_digits = map(int, reversed(str(base_number)))
        total = sum(
            digit * cls.FACTORS[idx % len(cls.FACTORS)]
            for idx, digit in enumerate(reversed_digits)
        )
        remainder = 11 - (total % 11)
        if remainder == 11:
            dv = "0"
        elif remainder == 10:
            dv = "K"
        else:
            dv = str(remainder)
        return f"{base_number}-{dv}"

    @classmethod
    def random_test_rut(cls, min_num: int = 20_000_000, max_num: int = 24_999_999) -> tuple[int, str]:
        base_number = random.randint(min_num, max_num)
        return base_number, cls.from_number(base_number)