package com.rizkysiregar.feedzmore.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Permintaan Anda berhasil diproses."),
    CREATED("201", "Data baru berhasil ditambahkan!"),
    INTERNAL_ERROR("500", "Terjadi kendala pada server kami. Mohon tunggu sebentar dan coba lagi nanti."),
    BAD_REQUEST("400", "Maaf, ada kendala saat memproses data Anda. Periksa kembali input Anda."),
    TOO_MANY_REQUEST("429", "Terlalu banyak permintaan. Mohon istirahat sejenak sebelum mencoba lagi."),
    RATE_LIMIT("429", "Tunggu sebenetar ya, lagi rame banget. Coba lagi %d detik lagi ya!"),
    TOO_MANY_REQUEST_OTP("429", "Anda telah mencapai batas percobaan. Silakan minta kode OTP baru dalam 5 menit."),
    BAD_REQUEST_FILE_TO_LARGE("400", "Ukuran file terlalu besar. Mohon unggah file dengan ukuran yang lebih kecil."),
    NOT_FOUND("404", "Ups, data yang Anda cari tidak ditemukan."),
    UNAUTHORIZED("401", "Sesi Anda telah berakhir. Silakan masuk (login) kembali."),
    GOOGLE_UNAUTHORIZED("401", "Invalid Google Token"),
    GOOGLE_TOKEN_MISS_MATCH("401", "Token client miss match"),
    GOOGLE_TOKEN_FAILED_TO_VERIFY("401", "Failed to verify Google token"),
    BAD_CREDENTIALS("401", "Email atau kata sandi yang Anda masukkan salah. Silakan coba lagi."),
    PARAMETER_NOT_FOUND("404", "Data pendukung tidak ditemukan. Mohon ulangi proses dari awal."),
    INVALID_OTP("400", "Kode OTP yang Anda masukkan salah. Periksa kembali kodenya, ya."),
    OTP_EXPIRED("404", "Kode OTP sudah tidak berlaku. Silakan minta kode yang baru."),
    INVALID_RESET_TOKEN("400", "Tautan perubahan kata sandi sudah tidak valid atau pernah digunakan."),
    USERNAME_ALREADY_TAKEN("400", "Nama pengguna ini sudah digunakan. Coba nama lain yang unik, ya!"),
    USER_NOT_FOUND("404", "User yang kamu cari tidak terdaftar"),
    EMAIL_ALREADY_TAKEN("400", "Email ini sudah terdaftar. Silakan gunakan email lain atau masuk ke akun Anda."),
    ;


    private final String code;
    private final String message;

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
