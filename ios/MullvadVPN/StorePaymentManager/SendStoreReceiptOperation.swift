//
//  SendStoreReceiptOperation.swift
//  MullvadVPN
//
//  Created by pronebird on 29/03/2022.
//  Copyright © 2025 Mullvad VPN AB. All rights reserved.
//

import Foundation
import MullvadLogging
import MullvadREST
import MullvadTypes
import Operations
import StoreKit

class SendStoreReceiptOperation: ResultOperation<REST.CreateApplePaymentResponse>, SKRequestDelegate,
    @unchecked Sendable {
    private let apiProxy: APIQuerying
    private let accountNumber: String

    private let forceRefresh: Bool
    private let receiptProperties: [String: Any]?
    private var refreshRequest: SKReceiptRefreshRequest?

    private var submitReceiptTask: Cancellable?

    private let logger = Logger(label: "SendStoreReceiptOperation")

    init(
        apiProxy: APIQuerying,
        accountNumber: String,
        forceRefresh: Bool,
        receiptProperties: [String: Any]?,
        completionHandler: @escaping CompletionHandler
    ) {
        self.apiProxy = apiProxy
        self.accountNumber = accountNumber
        self.forceRefresh = forceRefresh
        self.receiptProperties = receiptProperties

        super.init(
            dispatchQueue: .main,
            completionQueue: .main,
            completionHandler: completionHandler
        )
    }

    override func operationDidCancel() {
        refreshRequest?.cancel()
        refreshRequest = nil

        submitReceiptTask?.cancel()
        submitReceiptTask = nil
    }

    override func main() {
        // Pull receipt from AppStore if requested.
        guard !forceRefresh else {
            startRefreshRequest()
            return
        }

        // Read AppStore receipt from disk.
        do {
            let data = try readReceiptFromDisk()

            sendReceipt(data)
        } catch is StoreReceiptNotFound {
            // Pull receipt from AppStore if it's not cached locally.
            startRefreshRequest()
        } catch {
            logger.error(
                error: error,
                message: "Failed to read the AppStore receipt."
            )
            finish(result: .failure(StorePaymentManagerError.readReceipt(error)))
        }
    }

    // - MARK: SKRequestDelegate

    func requestDidFinish(_ request: SKRequest) {
        dispatchQueue.async {
            do {
                let data = try self.readReceiptFromDisk()

                self.sendReceipt(data)
            } catch {
                self.logger.error(
                    error: error,
                    message: "Failed to read the AppStore receipt after refresh."
                )
                self.finish(result: .failure(StorePaymentManagerError.readReceipt(error)))
            }
        }
    }

    func request(_ request: SKRequest, didFailWithError error: Error) {
        dispatchQueue.async {
            self.logger.error(
                error: error,
                message: "Failed to refresh the AppStore receipt."
            )
            self.finish(result: .failure(StorePaymentManagerError.readReceipt(error)))
        }
    }

    // MARK: - Private

    private func startRefreshRequest() {
        let refreshRequest = SKReceiptRefreshRequest(receiptProperties: receiptProperties)
        refreshRequest.delegate = self
        refreshRequest.start()

        self.refreshRequest = refreshRequest
    }

    private func readReceiptFromDisk() throws -> Data {
        guard let appStoreReceiptURL = Bundle.main.appStoreReceiptURL else {
            throw StoreReceiptNotFound()
        }

        do {
            return try Data(contentsOf: appStoreReceiptURL)
        } catch let error as CocoaError
            where error.code == .fileReadNoSuchFile || error.code == .fileNoSuchFile {
            throw StoreReceiptNotFound()
        } catch {
            throw error
        }
    }

    #if DEBUG
    private func sendReceipt(_ receiptData: Data) {
        submitReceiptTask = apiProxy.legacyStorekitPayment(
            accountNumber: accountNumber,
            request: LegacyStorekitRequest(receiptString: receiptData),
            retryStrategy: .default,
            completionHandler: { result in
                switch result {
                case let .success(response):
                    self.logger.info(
                        """
                        AppStore receipt was processed. \
                        Time added: \(response.timeAdded), \
                        New expiry: \(response.newExpiry.logFormatted)
                        """
                    )
                    self.finish(result: .success(response))

                case let .failure(error):
                    if error.isOperationCancellationError {
                        self.logger.debug("Receipt submission cancelled.")
                        self.finish(result: .failure(error))
                    } else {
                        self.logger.error(
                            error: error,
                            message: "Failed to send the AppStore receipt."
                        )
                        self.finish(result: .failure(StorePaymentManagerError.sendReceipt(error)))
                    }
                }
            }
        )
    }
    #else
    private func sendReceipt(_ receiptData: Data) {
        submitReceiptTask = apiProxy.createApplePayment(
            accountNumber: accountNumber,
            receiptString: receiptData
        ).execute(retryStrategy: .noRetry) { result in
            switch result {
            case let .success(response):
                self.logger.info(
                    """
                    AppStore receipt was processed. \
                    Time added: \(response.timeAdded), \
                    New expiry: \(response.newExpiry.logFormatted)
                    """
                )
                self.finish(result: .success(response))

            case let .failure(error):
                if error.isOperationCancellationError {
                    self.logger.debug("Receipt submission cancelled.")
                    self.finish(result: .failure(error))
                } else {
                    self.logger.error(
                        error: error,
                        message: "Failed to send the AppStore receipt."
                    )
                    self.finish(result: .failure(StorePaymentManagerError.sendReceipt(error)))
                }
            }
        }
    }
    #endif
}

struct StoreReceiptNotFound: LocalizedError {
    var errorDescription: String? {
        "AppStore receipt file does not exist on disk."
    }
}
