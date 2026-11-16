// Eigentone for Windows: a plain radix-2 FFT.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#include "eigentone/dsp/fft.h"

#include <cmath>
#include <numbers>
#include <stdexcept>
#include <utility>

namespace eigentone::dsp {

template <class T>
Fft<T>::Fft(size_t n) : n_(n), reversed_(n), twiddles_(n / 2) {
    if (n < 2 || (n & (n - 1))) throw std::invalid_argument("FFT size must be a power of two");
    size_t bits = 0;
    while ((size_t{1} << bits) < n) ++bits;
    for (size_t i = 0; i < n; ++i) {
        size_t r = 0;
        for (size_t b = 0; b < bits; ++b) r |= ((i >> b) & 1) << (bits - 1 - b);
        reversed_[i] = r;
    }
    for (size_t k = 0; k < n / 2; ++k) {
        const double a = -2.0 * std::numbers::pi * static_cast<double>(k) / static_cast<double>(n);
        twiddles_[k] = {static_cast<T>(std::cos(a)), static_cast<T>(std::sin(a))};
    }
}

template <class T>
void Fft<T>::run(std::complex<T>* data, bool inverse) const {
    for (size_t i = 0; i < n_; ++i) {
        if (i < reversed_[i]) std::swap(data[i], data[reversed_[i]]);
    }
    for (size_t len = 2; len <= n_; len <<= 1) {
        const size_t half = len / 2;
        const size_t stride = n_ / len;
        for (size_t start = 0; start < n_; start += len) {
            for (size_t k = 0; k < half; ++k) {
                std::complex<T> w = twiddles_[k * stride];
                if (inverse) w = std::conj(w);
                const std::complex<T> a = data[start + k];
                const std::complex<T> b = data[start + k + half] * w;
                data[start + k] = a + b;
                data[start + k + half] = a - b;
            }
        }
    }
    if (inverse) {
        const T scale = T(1) / static_cast<T>(n_);
        for (size_t i = 0; i < n_; ++i) data[i] *= scale;
    }
}

template class Fft<float>;
template class Fft<double>;

}  // namespace eigentone::dsp
