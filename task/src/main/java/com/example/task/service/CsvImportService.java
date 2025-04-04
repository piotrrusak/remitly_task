package com.example.task.service;

import org.springframework.core.io.ClassPathResource;

import com.example.task.dao.*;
import com.example.task.entity.*;
import com.opencsv.CSVReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

@Service
public class CsvImportService {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private BankNameRepository bankNameRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private CodeTypeRepository codeTypeRepository;

    @Autowired
    private CountryRepository countryRepository;

    public void importFromCsv() throws Exception {
        try (InputStream inputStream = new ClassPathResource("swift_codes.csv").getInputStream();
             InputStreamReader streamReader = new InputStreamReader(inputStream);
             CSVReader reader = new CSVReader(streamReader)) {

            String[] nextLine;
            reader.readNext(); // Pomijamy nagłówek

            List<Country> countries = countryRepository.findAll();
            List<BankName> bankNames = bankNameRepository.findAll();
            List<Address> addresses = addressRepository.findAll();
            List<CodeType> codeTypes = codeTypeRepository.findAll();
            List<Bank> existingBanks;

            while ((nextLine = reader.readNext()) != null) {

                existingBanks = bankRepository.findAllWithJoins();

                String dataCountryIso2 = nextLine[0];
                String dataSwiftCode = nextLine[1];
                String dataCodeType = nextLine[2];
                String dataBankName = nextLine[3];
                String dataAddress = nextLine[4];
                String dataTownName = nextLine[5];
                String dataCountryName = nextLine[6];
                String dataTimeZone = nextLine[7];

                boolean isHeadquarter = dataSwiftCode.endsWith("XXX");

                Country country = countries.stream()
                        .filter(c -> c.getCountryISO2().equalsIgnoreCase(dataCountryIso2))
                        .findFirst()
                        .orElseGet(() -> {
                            Country newCountry = new Country();
                            newCountry.setCountryISO2(dataCountryIso2);
                            newCountry.setCountryName(dataCountryName);
                            Country saved = countryRepository.save(newCountry);
                            countries.add(saved);
                            return saved;
                        });

                BankName bankName = bankNames.stream()
                        .filter(bn -> bn.getBankName().equalsIgnoreCase(dataBankName))
                        .findFirst()
                        .orElseGet(() -> {
                            BankName newBankName = new BankName();
                            newBankName.setBankName(dataBankName);
                            BankName saved = bankNameRepository.save(newBankName);
                            bankNames.add(saved);
                            return saved;
                        });

                Address address = addresses.stream()
                        .filter(a ->
                                a.getAddress().equalsIgnoreCase(dataAddress) &&
                                        a.getTownName().equalsIgnoreCase(dataTownName) &&
                                        a.getTimeZone().equalsIgnoreCase(dataTimeZone)
                        )
                        .findFirst()
                        .orElseGet(() -> {
                            Address newAddress = new Address();
                            newAddress.setAddress(dataAddress);
                            newAddress.setTownName(dataTownName);
                            newAddress.setTimeZone(dataTimeZone);
                            Address saved = addressRepository.save(newAddress);
                            addresses.add(saved);
                            return saved;
                        });

                CodeType codeType = codeTypes.stream()
                        .filter(ct -> ct.getCodeType().equalsIgnoreCase(dataCodeType))
                        .findFirst()
                        .orElseGet(() -> {
                            CodeType newCodeType = new CodeType();
                            newCodeType.setCodeType(dataCodeType);
                            CodeType saved = codeTypeRepository.save(newCodeType);
                            codeTypes.add(saved);
                            return saved;
                        });

                Bank bank = new Bank();
                bank.setSwiftCode(dataSwiftCode);
                bank.setHeadquarter(isHeadquarter); // boolean setter
                bank.setAddress(address);
                bank.setBankName(bankName);
                bank.setCountry(country);
                bank.setCodeType(codeType);


                if (!isHeadquarter) {
                    for (Bank hq : existingBanks) {
                        if (hq.isHeadquarter()) {
                            String hqSwift = hq.getSwiftCode();
                            if (hqSwift != null && hqSwift.length() >= 8 &&
                                    dataSwiftCode.length() >= 8 &&
                                    hqSwift.substring(0, 8).equals(dataSwiftCode.substring(0, 8))) {

                                bank.setHeadquarter(hq); // ustawiamy encję Bank, nie ID
                                break;
                            }
                        }
                    }
                }

                Bank savedBank = bankRepository.save(bank);
                existingBanks.add(savedBank);
            }
        }
    }
}