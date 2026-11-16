// Eigentone for Windows: a plain radix-2 FFT, used for the EQ's convolution and for designing its filter.
// Copyright (C) 2026 Dragjon. GPL-3.0-or-later.
#pragma once

#include <complex>
#include <cstddef>
#include <vector>

namespace eigentone::dsp {

/// An in-place FFT of a fixed power-of-two size. Tables are built once, so transforms never allocate.
template <class T>
class Fft {
public:
    explicit Fft(size_t n);

    size_t size() const { return n_; }
    void forward(std::complex<T>* data) const { run(data, false); }
    /// Scaled by 1/n, so forward then inverse returns the input.
    void inverse(std::complex<T>* data) const { run(data, true); }

private:
    void run(std::complex<T>* data, bool inverse) const;

    size_t n_;
    std::vector<size_t> reversed_;
    std::vector<std::complex<T>> twiddles_;  // e^(-2*pi*i*k/n) for k < n/2
};

extern template class Fft<float>;
extern template class Fft<double>;

}  // namespace eigentone::dsp
